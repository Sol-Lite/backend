package com.sollite.balance.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.balance.domain.entity.CashBalance;
import com.sollite.balance.domain.entity.CashLedger;
import com.sollite.balance.domain.entity.Holding;
import com.sollite.balance.domain.entity.PortfolioSnapshot;
import com.sollite.balance.domain.enums.CashEntryType;
import com.sollite.balance.domain.repository.CashBalanceRepository;
import com.sollite.balance.domain.repository.CashLedgerRepository;
import com.sollite.balance.domain.repository.HoldingRepository;
import com.sollite.balance.domain.repository.PortfolioSnapshotRepository;
import com.sollite.balance.dto.AssetFlowPointResponse;
import com.sollite.balance.dto.AssetFlowResponse;
import com.sollite.balance.dto.BalanceSummaryResponse;
import com.sollite.balance.dto.BuyableResponse;
import com.sollite.balance.dto.CashBalanceResponse;
import com.sollite.balance.dto.HoldingResponse;
import com.sollite.balance.dto.PriceQuote;
import com.sollite.balance.dto.PortfolioItem;
import com.sollite.balance.dto.PortfolioResponse;
import com.sollite.balance.exception.BalanceErrorCode;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private final AccountRepository accountRepository;
    private final SimulationRoundRepository simulationRoundRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final HoldingRepository holdingRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketService marketService;
    private final ForeignStockMarketService foreignStockMarketService;
    private final PriceLookupService priceLookupService;
    private final PortfolioSnapshotService portfolioSnapshotService;

    private static final BigDecimal SEED_MONEY = new BigDecimal("100000000");

    /**
     * 계좌 개설/리셋 시 초기 현금 잔고 생성
     */
    @Transactional
    public void initializeCashBalance(Account account, SimulationRound round, BigDecimal initialAmount) {
        cashBalanceRepository.save(CashBalance.builder()
                .account(account)
                .simulationRound(round)
                .currencyCode("KRW")
                .availableAmount(initialAmount)
                .totalAmount(initialAmount)
                .build());
        cashBalanceRepository.save(CashBalance.builder()
                .account(account)
                .simulationRound(round)
                .currencyCode("USD")
                .availableAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build());
    }

    /**
     * 계좌 폐쇄 전 현금 잔고 검증 — KRW/USD 모두 0원이어야 함
     */
    public void validateCashForClose(Long accountId, Long roundId) {
        List<CashBalance> balances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(accountId, roundId);
        boolean hasBalance = balances.stream()
                .anyMatch(cb -> cb.getTotalAmount().compareTo(BigDecimal.ZERO) > 0);
        if (hasBalance) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_CLOSE_BALANCE_REMAINS);
        }
    }

    /**
     * 계좌 폐쇄 전 현금을 0원으로 초기화합니다. KRW/USD 모두 처리하며 원장에 이력을 기록합니다.
     * 미체결 주문이 없을 때만 호출 가능합니다 (PIN은 AccountService에서 검증).
     */
    @Transactional
    public void resetCashForClose(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

        List<CashBalance> balances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(
                        account.getAccountId(), round.getSimulationRoundId());

        for (CashBalance cb : balances) {
            if (cb.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) continue;

            cashLedgerRepository.save(CashLedger.builder()
                    .account(account)
                    .simulationRound(round)
                    .currencyCode(cb.getCurrencyCode())
                    .entryType(CashEntryType.ACCOUNT_CLOSE_RESET)
                    .amountDelta(cb.getTotalAmount().negate())
                    .balanceAfter(BigDecimal.ZERO)
                    .referenceType("ACCOUNT_CLOSE")
                    .referenceId(String.valueOf(account.getAccountId()))
                    .build());

            cb.resetToZero();
        }
    }

    /**
     * 계좌 폐쇄 전 보유 종목 검증 — 보유 수량 > 0 이면 예외
     */
    public void validateHoldingsForClose(Long accountId, Long roundId) {
        boolean hasHoldings = holdingRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(accountId, roundId)
                .stream().anyMatch(h -> h.getHoldingQuantity() > 0);
        if (hasHoldings) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_CLOSE_HOLDINGS_REMAIN);
        }
    }

    /**
     * 예수금 조회 (통화별 available / total)
     */
    public List<CashBalanceResponse> getCashBalances(Long userId) {
        var pair = resolveAccountAndRound(userId);
        List<CashBalance> balances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(
                        pair.account.getAccountId(), pair.round.getSimulationRoundId());
        return balances.stream().map(CashBalanceResponse::from).toList();
    }

    /**
     * 매수 가능 금액 조회
     * stockCode + marketType → 종목의 통화 결정 → 해당 통화 가용금액 / 주문가격으로 최대 수량 계산
     */
    public BuyableResponse getBuyableAmount(Long userId, String stockCode, String marketType, BigDecimal orderPrice) {
        var pair = resolveAccountAndRound(userId);

        Instrument instrument = instrumentRepository
                .findByStockCodeAndMarketType(stockCode, marketType)
                .orElseThrow(() -> new BusinessException(BalanceErrorCode.INSTRUMENT_NOT_FOUND));

        CashBalance cashBalance = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndCurrencyCode(
                        pair.account.getAccountId(), pair.round.getSimulationRoundId(),
                        instrument.getCurrencyCode())
                .orElseThrow(() -> new BusinessException(BalanceErrorCode.CASH_BALANCE_NOT_FOUND));

        BigDecimal available = cashBalance.getAvailableAmount();

        // orderPrice가 없으면 현재가 조회
        BigDecimal price = orderPrice;
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = fetchCurrentPrice(instrument);
        }

        long maxQty = 0;
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            maxQty = available.divide(price, 0, RoundingMode.DOWN).longValue();
        }

        return new BuyableResponse(available, price, maxQty);
    }

    /**
     * 국내 주식 잔고
     */
    private static final List<String> DOMESTIC_MARKET_TYPES = List.of("KOSPI", "KOSDAQ");
    private static final List<String> OVERSEAS_MARKET_TYPES = List.of("NASDAQ", "NYSE", "AMEX");

    public List<HoldingResponse> getDomesticHoldings(Long userId) {
        var pair = resolveAccountAndRound(userId);
        List<Holding> holdings = holdingRepository.findActiveHoldings(
                pair.account.getAccountId(), pair.round.getSimulationRoundId(), DOMESTIC_MARKET_TYPES);

        Map<Long, PriceQuote> prices = priceLookupService.resolveDomesticPriceQuotesByInstrument(holdings.stream()
                .map(h -> Map.entry(
                        h.getInstrument().getInstrumentId(),
                        h.getInstrument().getStockCode()))
                .toList());

        return holdings.stream()
                .map(h -> HoldingResponse.from(h, prices.get(h.getInstrument().getInstrumentId())))
                .toList();
    }

    /**
     * 해외 주식 잔고
     */
    public List<HoldingResponse> getOverseasHoldings(Long userId) {
        var pair = resolveAccountAndRound(userId);
        List<Holding> holdings = holdingRepository.findActiveHoldings(
                pair.account.getAccountId(), pair.round.getSimulationRoundId(), OVERSEAS_MARKET_TYPES);

        if (holdings.isEmpty()) return List.of();

        Map<Long, PriceQuote> prices = priceLookupService.resolveForeignPriceQuotesByInstrument(holdings.stream()
                .map(h -> Map.entry(
                        h.getInstrument().getInstrumentId(),
                        Map.entry(
                                h.getInstrument().getStockCode(),
                                toForeignExchcd(h.getInstrument().getExchangeCode()))))
                .toList());
        BigDecimal usdKrwRate = resolveUsdKrwRate();

        return holdings.stream()
                .map(h -> HoldingResponse.from(h, prices.get(h.getInstrument().getInstrumentId()), usdKrwRate))
                .toList();
    }

    private String toForeignExchcd(String exchangeCode) {
        if (exchangeCode == null) {
            return "82";
        }
        return switch (exchangeCode) {
            case "NYS", "AMS" -> "81";
            case "NAS" -> "82";
            default -> "82";
        };
    }

    /**
     * 총 평가자산 요약 — 현재가 기반 실시간 평가 (모든 금액 KRW 기준)
     */
    public BalanceSummaryResponse getBalanceSummary(Long userId) {
        var pair = resolveAccountAndRound(userId);
        ValuationSummary valuation = calculateValuation(pair);

        BigDecimal totalStockUnrealizedProfitLoss = valuation.totalStockEvaluation().subtract(valuation.totalStockBuyAmount());
        BigDecimal totalStockUnrealizedProfitLossRate = valuation.totalStockBuyAmount().compareTo(BigDecimal.ZERO) > 0
                ? totalStockUnrealizedProfitLoss.divide(valuation.totalStockBuyAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalAssets = valuation.totalAssets();
        BigDecimal initialSeedAmount = pair.round.getInitialSeedAmount();
        BigDecimal accountProfitLoss = totalAssets.subtract(initialSeedAmount);
        BigDecimal accountProfitLossRate = initialSeedAmount.compareTo(BigDecimal.ZERO) > 0
                ? accountProfitLoss.divide(initialSeedAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new BalanceSummaryResponse(
                valuation.totalCashKrw(),
                valuation.totalStockBuyAmount(),
                valuation.totalStockEvaluation(),
                totalStockUnrealizedProfitLoss,
                totalStockUnrealizedProfitLossRate,
                totalAssets,
                accountProfitLoss,
                accountProfitLossRate,
                valuation.cashBalances().stream().map(CashBalanceResponse::from).toList(),
                valuation.holdings().size()
        );
    }

    /**
     * 자산 흐름 조회
     * 외부 API 호출(현재가 조회)이 포함되므로 트랜잭션 분리:
     * 1. calculateValuation: 외부 IO, 트랜잭션 없음
     * 2. portfolioSnapshotService.upsertTodaySnapshot: DB 저장, REQUIRES_NEW 트랜잭션 (별도 Bean)
     * 3. 스냅샷 조회: 읽기 전용
     */
    public AssetFlowResponse getAssetFlow(Long userId, String range) {
        var pair = resolveAccountAndRound(userId);

        // 1단계: 외부 API 호출 (트랜잭션 없음, 느린 IO)
        ValuationSummary valuation = calculateValuation(pair);

        // 2단계: 스냅샷 저장 (별도 Bean에서 새 트랜잭션으로 실행)
        portfolioSnapshotService.upsertTodaySnapshot(
                pair.account,
                pair.round,
                valuation.totalAssets(),
                valuation.cashKrwAmount(),
                valuation.cashUsdAmount(),
                valuation.usdKrwRate(),
                valuation.totalStockEvaluation());

        // 3단계: 스냅샷 조회 (읽기 전용)
        LocalDate fromDate = resolveFlowStartDate(range, pair.round);
        List<AssetFlowPointResponse> points = portfolioSnapshotRepository
                .findRange(pair.account.getAccountId(), pair.round.getSimulationRoundId(), fromDate)
                .stream()
                .map(snapshot -> toAssetFlowPoint(snapshot, pair.round.getInitialSeedAmount()))
                .toList();

        return new AssetFlowResponse(points);
    }

    /**
     * 포트폴리오 파이차트 구성 비중 — 현재가 기반 실시간 평가 (모든 금액 KRW 기준)
     * 현금은 KRW/USD 각각 아이템으로 분리 (둘 다 KRW 환산 evalAmount)
     */
    public PortfolioResponse getPortfolio(Long userId) {
        var pair = resolveAccountAndRound(userId);
        Long accountId = pair.account.getAccountId();
        Long roundId = pair.round.getSimulationRoundId();

        List<CashBalance> cashBalances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(accountId, roundId);
        List<Holding> holdings = holdingRepository.findAllActiveHoldings(accountId, roundId);

        // USD 노출이 있을 때만 환율 조회
        boolean hasUsdExposure = cashBalances.stream()
                .anyMatch(cb -> "USD".equals(cb.getCurrencyCode())
                        && cb.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                || holdings.stream().anyMatch(h -> "USD".equals(h.getInstrument().getCurrencyCode()));
        BigDecimal usdKrwRate = hasUsdExposure ? resolveUsdKrwRate() : BigDecimal.ZERO;

        Map<Long, BigDecimal> priceMap = resolveHoldingPrices(holdings);

        // 총자산 = 모든 현금(KRW 환산) + 모든 주식 평가금액(KRW)
        BigDecimal totalCashKrw = cashBalances.stream()
                .map(cb -> toKrw(cb.getCurrencyCode(), cb.getTotalAmount(), usdKrwRate))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalStockEvaluation = BigDecimal.ZERO;
        for (Holding h : holdings) {
            String currency = h.getInstrument().getCurrencyCode();
            long qty = h.getHoldingQuantity();
            BigDecimal price = priceMap.get(h.getInstrument().getInstrumentId());
            BigDecimal fallbackBuyKrw = h.getAvgBuyPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .multiply(h.getAvgBuyExchangeRate())
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal evalKrw = price != null
                    ? toKrw(currency, price.multiply(BigDecimal.valueOf(qty)), usdKrwRate)
                    : fallbackBuyKrw;
            totalStockEvaluation = totalStockEvaluation.add(evalKrw);
        }

        BigDecimal totalAssets = totalCashKrw.add(totalStockEvaluation);

        List<PortfolioItem> items = new ArrayList<>();

        // 현금 아이템: 통화별로 분리
        for (CashBalance cb : cashBalances) {
            BigDecimal evalKrw = toKrw(cb.getCurrencyCode(), cb.getTotalAmount(), usdKrwRate);
            if (evalKrw.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal weight = totalAssets.compareTo(BigDecimal.ZERO) > 0
                    ? evalKrw.divide(totalAssets, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String label = "KRW".equals(cb.getCurrencyCode()) ? "현금(KRW)" : "현금(USD)";
            items.add(new PortfolioItem(label, "CASH", evalKrw, weight, null, null));
        }

        // 주식 아이템
        for (Holding h : holdings) {
            String currency = h.getInstrument().getCurrencyCode();
            long qty = h.getHoldingQuantity();
            BigDecimal price = priceMap.get(h.getInstrument().getInstrumentId());
            BigDecimal buyKrw = h.getAvgBuyPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .multiply(h.getAvgBuyExchangeRate())
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal evalKrw = price != null
                    ? toKrw(currency, price.multiply(BigDecimal.valueOf(qty)), usdKrwRate)
                    : buyKrw;
            BigDecimal weight = totalAssets.compareTo(BigDecimal.ZERO) > 0
                    ? evalKrw.divide(totalAssets, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal profitLoss = price != null ? evalKrw.subtract(buyKrw) : null;
            BigDecimal profitLossRate = null;
            if (profitLoss != null && buyKrw.compareTo(BigDecimal.ZERO) > 0) {
                profitLossRate = profitLoss.divide(buyKrw, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }
            items.add(new PortfolioItem(
                    h.getInstrument().getStockName(),
                    "STOCK",
                    evalKrw,
                    weight,
                    profitLoss,
                    profitLossRate
            ));
        }

        return new PortfolioResponse(totalAssets, items);
    }

    // -----------------------------------------------------------------------

    /** 금액을 KRW로 환산. USD인 경우 usdKrwRate 적용. */
    private BigDecimal toKrw(String currencyCode, BigDecimal amount, BigDecimal usdKrwRate) {
        if (amount == null) return BigDecimal.ZERO;
        return switch (currencyCode) {
            case "KRW" -> amount;
            case "USD" -> amount.multiply(usdKrwRate).setScale(0, RoundingMode.HALF_UP);
            default -> throw new IllegalArgumentException("unsupported currency: " + currencyCode);
        };
    }

    /** USD/KRW 환율 조회. 없으면 503 예외. */
    private BigDecimal resolveUsdKrwRate() {
        BigDecimal rate = priceLookupService.resolveUsdKrwRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BalanceErrorCode.EXCHANGE_RATE_UNAVAILABLE);
        }
        return rate;
    }

    private record AccountRound(Account account, SimulationRound round) {}
    private record ValuationSummary(
            List<CashBalance> cashBalances,
            List<Holding> holdings,
            BigDecimal usdKrwRate,
            BigDecimal cashKrwAmount,
            BigDecimal cashUsdAmount,
            BigDecimal totalCashKrw,
            BigDecimal totalStockBuyAmount,
            BigDecimal totalStockEvaluation,
            BigDecimal totalAssets
    ) {}

    private AccountRound resolveAccountAndRound(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));
        return new AccountRound(account, round);
    }

    /**
     * 보유 종목 목록의 현재가를 국내/해외 구분해서 조회
     * key = instrumentId (stockCode 기반 충돌 방지)
     */
    private Map<Long, BigDecimal> resolveHoldingPrices(List<Holding> holdings) {
        Map<Long, BigDecimal> prices = new java.util.HashMap<>();
        prices.putAll(priceLookupService.resolveDomesticPricesByInstrument(holdings.stream()
                .filter(h -> DOMESTIC_MARKET_TYPES.contains(h.getInstrument().getMarketType()))
                .map(h -> Map.entry(
                        h.getInstrument().getInstrumentId(),
                        h.getInstrument().getStockCode()))
                .toList()));
        prices.putAll(priceLookupService.resolveForeignPricesByInstrument(holdings.stream()
                .filter(h -> OVERSEAS_MARKET_TYPES.contains(h.getInstrument().getMarketType()))
                .map(h -> Map.entry(
                        h.getInstrument().getInstrumentId(),
                        Map.entry(
                                h.getInstrument().getStockCode(),
                                toForeignExchcd(h.getInstrument().getExchangeCode()))))
                .toList()));
        return prices;
    }

    private ValuationSummary calculateValuation(AccountRound pair) {
        Long accountId = pair.account.getAccountId();
        Long roundId = pair.round.getSimulationRoundId();

        List<CashBalance> cashBalances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(accountId, roundId);
        List<Holding> holdings = holdingRepository.findAllActiveHoldings(accountId, roundId);

        boolean hasUsdExposure = cashBalances.stream()
                .anyMatch(cb -> "USD".equals(cb.getCurrencyCode())
                        && cb.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                || holdings.stream().anyMatch(h -> "USD".equals(h.getInstrument().getCurrencyCode()));
        BigDecimal usdKrwRate = hasUsdExposure ? resolveUsdKrwRate() : BigDecimal.ZERO;

        BigDecimal cashKrwAmount = cashBalances.stream()
                .filter(cb -> "KRW".equals(cb.getCurrencyCode()))
                .map(CashBalance::getTotalAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal cashUsdAmount = cashBalances.stream()
                .filter(cb -> "USD".equals(cb.getCurrencyCode()))
                .map(CashBalance::getTotalAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        BigDecimal totalCashKrw = cashBalances.stream()
                .map(cb -> toKrw(cb.getCurrencyCode(), cb.getTotalAmount(), usdKrwRate))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> priceMap = resolveHoldingPrices(holdings);

        BigDecimal totalStockBuyAmount = BigDecimal.ZERO;
        BigDecimal totalStockEvaluation = BigDecimal.ZERO;
        for (Holding h : holdings) {
            String currency = h.getInstrument().getCurrencyCode();
            long qty = h.getHoldingQuantity();
            BigDecimal buyKrw = h.getAvgBuyPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .multiply(h.getAvgBuyExchangeRate())
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal price = priceMap.get(h.getInstrument().getInstrumentId());
            BigDecimal evalKrw = price != null
                    ? toKrw(currency, price.multiply(BigDecimal.valueOf(qty)), usdKrwRate)
                    : buyKrw;
            totalStockBuyAmount = totalStockBuyAmount.add(buyKrw);
            totalStockEvaluation = totalStockEvaluation.add(evalKrw);
        }

        BigDecimal totalAssets = totalCashKrw.add(totalStockEvaluation);
        return new ValuationSummary(
                cashBalances,
                holdings,
                usdKrwRate,
                cashKrwAmount,
                cashUsdAmount,
                totalCashKrw,
                totalStockBuyAmount,
                totalStockEvaluation,
                totalAssets
        );
    }


    private BigDecimal computeDailyReturnRate(Long accountId, Long roundId, LocalDate snapshotDate, BigDecimal totalAssets) {
        return portfolioSnapshotRepository.findPreviousSnapshots(
                accountId, roundId, snapshotDate, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(previous -> previous.getTotalValue().compareTo(BigDecimal.ZERO) > 0
                        ? totalAssets.divide(previous.getTotalValue(), 6, RoundingMode.HALF_UP)
                                .subtract(BigDecimal.ONE)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private LocalDate resolveFlowStartDate(String range, SimulationRound round) {
        LocalDate today = LocalDate.now();
        LocalDate roundStartDate = round.getStartedAt().toLocalDate();
        if (range == null || range.isBlank()) {
            return roundStartDate;
        }
        LocalDate candidate = switch (range.trim().toUpperCase()) {
            case "1W" -> today.minusWeeks(1).plusDays(1);
            case "1M" -> today.minusMonths(1).plusDays(1);
            case "3M" -> today.minusMonths(3).plusDays(1);
            case "YTD" -> LocalDate.of(today.getYear(), 1, 1);
            case "ALL" -> roundStartDate;
            default -> roundStartDate;
        };
        return candidate.isBefore(roundStartDate) ? roundStartDate : candidate;
    }

    private AssetFlowPointResponse toAssetFlowPoint(PortfolioSnapshot snapshot, BigDecimal initialSeedAmount) {
        BigDecimal cumulativeReturnRate = initialSeedAmount.compareTo(BigDecimal.ZERO) > 0
                ? snapshot.getTotalValue().divide(initialSeedAmount, 6, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new AssetFlowPointResponse(
                snapshot.getSnapshotDate(),
                snapshot.getTotalValue().setScale(0, RoundingMode.HALF_UP),
                snapshot.getDailyReturn() != null ? snapshot.getDailyReturn() : BigDecimal.ZERO,
                cumulativeReturnRate
        );
    }

    private BigDecimal fetchCurrentPrice(Instrument instrument) {
        try {
            if (List.of("NASDAQ", "NYSE", "AMEX").contains(instrument.getMarketType())) {
                String exchcd = resolveExchangeCode(instrument.getExchangeCode());
                var response = foreignStockMarketService.getCurrentPriceFresh(instrument.getStockCode(), exchcd);
                return BigDecimal.valueOf(response.price());
            } else {
                var response = marketService.getCurrentPriceFresh(instrument.getStockCode());
                return BigDecimal.valueOf(response.currentPrice());
            }
        } catch (Exception e) {
            log.warn("[BALANCE] 현재가 조회 실패 — stockCode={}, error={}",
                    instrument.getStockCode(), e.getMessage());
            return null;
        }
    }

    private String resolveExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return "82";
        return switch (exchangeCode.trim().toUpperCase()) {
            case "NAS", "NASDAQ", "82" -> "82";
            case "NYS", "NYSE", "AMS", "AMEX", "81" -> "81";
            default -> "82";
        };
    }
}
