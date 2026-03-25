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
import com.sollite.balance.domain.enums.CashEntryType;
import com.sollite.balance.domain.repository.CashBalanceRepository;
import com.sollite.balance.domain.repository.CashLedgerRepository;
import com.sollite.balance.domain.repository.HoldingRepository;
import com.sollite.balance.dto.BalanceSummaryResponse;
import com.sollite.balance.dto.BuyableResponse;
import com.sollite.balance.dto.CashBalanceResponse;
import com.sollite.balance.dto.HoldingResponse;
import com.sollite.balance.dto.PortfolioItem;
import com.sollite.balance.dto.PortfolioResponse;
import com.sollite.balance.exception.BalanceErrorCode;
import com.sollite.foreignmarket.dto.ForeignCurrentPriceResponse;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final InstrumentRepository instrumentRepository;
    private final MarketService marketService;
    private final ForeignStockMarketService foreignStockMarketService;
    private final PriceLookupService priceLookupService;

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

        List<String> stockCodes = holdings.stream()
                .map(h -> h.getInstrument().getStockCode())
                .toList();
        Map<String, BigDecimal> prices = priceLookupService.resolveDomesticPrices(stockCodes);

        return holdings.stream()
                .map(h -> HoldingResponse.from(h, prices.get(h.getInstrument().getStockCode())))
                .toList();
    }

    /**
     * 해외 주식 잔고
     */
    public List<HoldingResponse> getOverseasHoldings(Long userId) {
        var pair = resolveAccountAndRound(userId);
        List<Holding> holdings = holdingRepository.findActiveHoldings(
                pair.account.getAccountId(), pair.round.getSimulationRoundId(), OVERSEAS_MARKET_TYPES);

        List<Map.Entry<String, String>> symbolExchcdPairs = holdings.stream()
                .map(h -> Map.entry(
                        h.getInstrument().getStockCode(),
                        toForeignExchcd(h.getInstrument().getExchangeCode())))
                .toList();
        Map<String, BigDecimal> prices = priceLookupService.resolveForeignPrices(symbolExchcdPairs);

        return holdings.stream()
                .map(h -> HoldingResponse.from(h, prices.get(h.getInstrument().getStockCode())))
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
     * 총 평가자산 요약 — 현재가 기반 실시간 평가
     */
    public BalanceSummaryResponse getBalanceSummary(Long userId) {
        var pair = resolveAccountAndRound(userId);
        Long accountId = pair.account.getAccountId();
        Long roundId = pair.round.getSimulationRoundId();

        List<CashBalance> cashBalances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(accountId, roundId);
        BigDecimal totalCash = cashBalances.stream()
                .filter(cb -> "KRW".equals(cb.getCurrencyCode()))
                .map(CashBalance::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Holding> holdings = holdingRepository.findAllActiveHoldings(accountId, roundId);
        Map<String, BigDecimal> priceMap = resolveHoldingPrices(holdings);

        BigDecimal totalStockBuyAmount = BigDecimal.ZERO;
        BigDecimal totalStockEvaluation = BigDecimal.ZERO;
        for (Holding h : holdings) {
            BigDecimal buyAmt = h.getAvgBuyPrice().multiply(BigDecimal.valueOf(h.getHoldingQuantity()));
            BigDecimal price = priceMap.get(h.getInstrument().getStockCode());
            BigDecimal evalAmt = price != null
                    ? price.multiply(BigDecimal.valueOf(h.getHoldingQuantity()))
                    : buyAmt; // 현재가 없으면 매입금액으로 폴백
            totalStockBuyAmount = totalStockBuyAmount.add(buyAmt);
            totalStockEvaluation = totalStockEvaluation.add(evalAmt);
        }

        BigDecimal totalStockUnrealizedProfitLoss = totalStockEvaluation.subtract(totalStockBuyAmount);
        BigDecimal totalStockUnrealizedProfitLossRate = totalStockBuyAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalStockUnrealizedProfitLoss.divide(totalStockBuyAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalAssets = totalCash.add(totalStockEvaluation);
        BigDecimal initialSeedAmount = pair.round.getInitialSeedAmount();
        BigDecimal accountProfitLoss = totalAssets.subtract(initialSeedAmount);
        BigDecimal accountProfitLossRate = initialSeedAmount.compareTo(BigDecimal.ZERO) > 0
                ? accountProfitLoss.divide(initialSeedAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new BalanceSummaryResponse(
                totalCash,
                totalStockBuyAmount,
                totalStockEvaluation,
                totalStockUnrealizedProfitLoss,
                totalStockUnrealizedProfitLossRate,
                totalAssets,
                accountProfitLoss,
                accountProfitLossRate,
                cashBalances.stream().map(CashBalanceResponse::from).toList(),
                holdings.size()
        );
    }

    /**
     * 포트폴리오 파이차트 구성 비중 — 현재가 기반 실시간 평가
     */
    public PortfolioResponse getPortfolio(Long userId) {
        var pair = resolveAccountAndRound(userId);
        Long accountId = pair.account.getAccountId();
        Long roundId = pair.round.getSimulationRoundId();

        List<CashBalance> cashBalances = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(accountId, roundId);
        BigDecimal totalCash = cashBalances.stream()
                .filter(cb -> "KRW".equals(cb.getCurrencyCode()))
                .map(CashBalance::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Holding> holdings = holdingRepository.findAllActiveHoldings(accountId, roundId);
        Map<String, BigDecimal> priceMap = resolveHoldingPrices(holdings);

        BigDecimal totalStockEvaluation = BigDecimal.ZERO;
        for (Holding h : holdings) {
            BigDecimal price = priceMap.get(h.getInstrument().getStockCode());
            BigDecimal evalAmt = price != null
                    ? price.multiply(BigDecimal.valueOf(h.getHoldingQuantity()))
                    : h.getAvgBuyPrice().multiply(BigDecimal.valueOf(h.getHoldingQuantity()));
            totalStockEvaluation = totalStockEvaluation.add(evalAmt);
        }

        BigDecimal totalAssets = totalCash.add(totalStockEvaluation);

        List<PortfolioItem> items = new ArrayList<>();

        if (totalCash.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cashWeight = totalAssets.compareTo(BigDecimal.ZERO) > 0
                    ? totalCash.divide(totalAssets, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(new PortfolioItem("현금", "CASH", totalCash, cashWeight, null, null));
        }

        for (Holding h : holdings) {
            BigDecimal price = priceMap.get(h.getInstrument().getStockCode());
            BigDecimal buyAmt = h.getAvgBuyPrice().multiply(BigDecimal.valueOf(h.getHoldingQuantity()));
            BigDecimal evalAmt = price != null
                    ? price.multiply(BigDecimal.valueOf(h.getHoldingQuantity()))
                    : buyAmt;
            BigDecimal weight = totalAssets.compareTo(BigDecimal.ZERO) > 0
                    ? evalAmt.divide(totalAssets, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal profitLoss = price != null ? evalAmt.subtract(buyAmt) : null;
            BigDecimal profitLossRate = null;
            if (profitLoss != null && buyAmt.compareTo(BigDecimal.ZERO) > 0) {
                profitLossRate = profitLoss.divide(buyAmt, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }
            items.add(new PortfolioItem(
                    h.getInstrument().getStockName(),
                    "STOCK",
                    evalAmt,
                    weight,
                    profitLoss,
                    profitLossRate
            ));
        }

        return new PortfolioResponse(totalAssets, items);
    }

    // -----------------------------------------------------------------------

    private record AccountRound(Account account, SimulationRound round) {}

    private AccountRound resolveAccountAndRound(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));
        return new AccountRound(account, round);
    }

    /**
     * 보유 종목 목록의 현재가를 국내/해외 구분해서 한 번에 조회
     * key = stockCode, value = currentPrice (조회 실패 시 key 없음)
     */
    private Map<String, BigDecimal> resolveHoldingPrices(List<Holding> holdings) {
        List<String> domesticCodes = holdings.stream()
                .filter(h -> DOMESTIC_MARKET_TYPES.contains(h.getInstrument().getMarketType()))
                .map(h -> h.getInstrument().getStockCode())
                .toList();

        List<Map.Entry<String, String>> foreignPairs = holdings.stream()
                .filter(h -> OVERSEAS_MARKET_TYPES.contains(h.getInstrument().getMarketType()))
                .map(h -> Map.entry(
                        h.getInstrument().getStockCode(),
                        toForeignExchcd(h.getInstrument().getExchangeCode())))
                .toList();

        Map<String, BigDecimal> prices = new java.util.HashMap<>();
        prices.putAll(priceLookupService.resolveDomesticPrices(domesticCodes));
        prices.putAll(priceLookupService.resolveForeignPrices(foreignPairs));
        return prices;
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
