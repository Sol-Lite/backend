package com.sollite.exchange.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.balance.domain.entity.CashBalance;
import com.sollite.balance.domain.entity.CashLedger;
import com.sollite.balance.domain.enums.CashEntryType;
import com.sollite.balance.domain.repository.CashBalanceRepository;
import com.sollite.balance.domain.repository.CashLedgerRepository;
import com.sollite.balance.exception.BalanceErrorCode;
import com.sollite.balance.service.PriceLookupService;
import com.sollite.exchange.domain.entity.FxOrder;
import com.sollite.exchange.domain.enums.FxOrderStatus;
import com.sollite.exchange.domain.repository.FxOrderRepository;
import com.sollite.exchange.dto.ExchangeAvailableResponse;
import com.sollite.exchange.dto.ExchangeRateResponse;
import com.sollite.exchange.dto.ExchangeRequest;
import com.sollite.exchange.dto.ExchangeResponse;
import com.sollite.exchange.exception.ExchangeErrorCode;
import com.sollite.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("KRW", "USD");
    private static final String REQUEST_CHANNEL = "API";

    private final AccountRepository accountRepository;
    private final SimulationRoundRepository simulationRoundRepository;
    private final CashBalanceRepository cashBalanceRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final FxOrderRepository fxOrderRepository;
    private final PriceLookupService priceLookupService;

    /**
     * 환전 가능 금액 조회 (미리보기)
     */
    @Transactional(readOnly = true)
    public ExchangeAvailableResponse getAvailable(Long userId, String fromCurrency, String toCurrency,
                                                   BigDecimal requestAmount) {
        validateCurrencyPair(fromCurrency, toCurrency);

        var pair = resolveAccountAndRound(userId);
        BigDecimal rate = requireExchangeRate();

        CashBalance fromBalance = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndCurrencyCode(
                        pair.account().getAccountId(), pair.round().getSimulationRoundId(), fromCurrency)
                .orElseThrow(() -> new BusinessException(BalanceErrorCode.CASH_BALANCE_NOT_FOUND));

        BigDecimal available = fromBalance.getAvailableAmount();
        BigDecimal estimated = null;
        if (requestAmount != null && requestAmount.compareTo(BigDecimal.ZERO) > 0) {
            estimated = calculateReceiveAmount(fromCurrency, requestAmount, rate);
        }

        return new ExchangeAvailableResponse(fromCurrency, toCurrency, available, rate, estimated);
    }

    /**
     * 현재 USD/KRW 환율 조회 (1 USD = X KRW)
     */
    @Transactional(readOnly = true)
    public ExchangeRateResponse getUsdKrwRate() {
        return new ExchangeRateResponse("USD", "KRW", requireExchangeRate());
    }

    /**
     * 환전 실행
     *
     * 트랜잭션 처리 순서:
     * 1. 잠금 순서 고정: KRW → USD (알파벳 순, 데드락 방지)
     * 2. from 잔고 검증 (available >= requestAmount)
     * 3. 환율 재조회 (서버 시점 확정)
     * 4. 잔고 차감/증가
     * 5. 현금 원장 기록
     * 6. FxOrder 저장
     */
    @Transactional
    public ExchangeResponse exchange(Long userId, ExchangeRequest request) {
        validateCurrencyPair(request.fromCurrency(), request.toCurrency());
        validateAmount(request.requestAmount());

        var pair = resolveAccountAndRound(userId);
        Long accountId = pair.account().getAccountId();
        Long roundId = pair.round().getSimulationRoundId();

        // 잠금 순서: KRW 먼저, USD 다음 (알파벳 순 고정)
        String firstLock = "KRW";
        String secondLock = "USD";

        CashBalance krwBalance = cashBalanceRepository.findForUpdate(accountId, roundId, firstLock)
                .orElseThrow(() -> new BusinessException(BalanceErrorCode.CASH_BALANCE_NOT_FOUND));
        CashBalance usdBalance = cashBalanceRepository.findForUpdate(accountId, roundId, secondLock)
                .orElseThrow(() -> new BusinessException(BalanceErrorCode.CASH_BALANCE_NOT_FOUND));

        CashBalance fromBalance = "KRW".equals(request.fromCurrency()) ? krwBalance : usdBalance;
        CashBalance toBalance = "KRW".equals(request.toCurrency()) ? krwBalance : usdBalance;

        if (fromBalance.getAvailableAmount().compareTo(request.requestAmount()) < 0) {
            throw new BusinessException(ExchangeErrorCode.INSUFFICIENT_BALANCE);
        }

        BigDecimal rate = requireExchangeRate();
        BigDecimal receiveAmount = calculateReceiveAmount(request.fromCurrency(), request.requestAmount(), rate);
        LocalDateTime now = LocalDateTime.now();

        fromBalance.deductForFx(request.requestAmount());
        toBalance.addFromFx(receiveAmount);

        String fxOrderNo = generateFxOrderNo();

        String rateNote = "USD/KRW=" + rate.toPlainString();

        cashLedgerRepository.save(CashLedger.builder()
                .account(pair.account())
                .simulationRound(pair.round())
                .currencyCode(request.fromCurrency())
                .entryType(CashEntryType.FX_OUT)
                .amountDelta(request.requestAmount().negate())
                .balanceAfter(fromBalance.getTotalAmount())
                .referenceType("FX_ORDER")
                .referenceId(fxOrderNo)
                .note(rateNote)
                .build());

        cashLedgerRepository.save(CashLedger.builder()
                .account(pair.account())
                .simulationRound(pair.round())
                .currencyCode(request.toCurrency())
                .entryType(CashEntryType.FX_IN)
                .amountDelta(receiveAmount)
                .balanceAfter(toBalance.getTotalAmount())
                .referenceType("FX_ORDER")
                .referenceId(fxOrderNo)
                .note(rateNote)
                .build());

        FxOrder fxOrder = fxOrderRepository.save(FxOrder.builder()
                .account(pair.account())
                .simulationRound(pair.round())
                .fxOrderNo(fxOrderNo)
                .requestChannel(REQUEST_CHANNEL)
                .fromCurrencyCode(request.fromCurrency())
                .toCurrencyCode(request.toCurrency())
                .requestAmount(request.requestAmount())
                .appliedRate(rate)
                .feeAmount(BigDecimal.ZERO)
                .receiveAmount(receiveAmount)
                .fxOrderStatus(FxOrderStatus.COMPLETED)
                .requestedAt(now)
                .completedAt(now)
                .build());

        return new ExchangeResponse(
                fxOrder.getFxOrderId(),
                fxOrder.getFxOrderNo(),
                request.fromCurrency(),
                request.toCurrency(),
                request.requestAmount(),
                rate,
                BigDecimal.ZERO,
                receiveAmount,
                fromBalance.getTotalAmount(),
                toBalance.getTotalAmount(),
                now
        );
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private BigDecimal requireExchangeRate() {
        BigDecimal rate = priceLookupService.resolveUsdKrwRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ExchangeErrorCode.EXCHANGE_RATE_UNAVAILABLE);
        }
        return rate;
    }

    /**
     * KRW→USD: receive = requestAmount / rate
     * USD→KRW: receive = requestAmount * rate
     */
    private BigDecimal calculateReceiveAmount(String fromCurrency, BigDecimal requestAmount, BigDecimal rate) {
        if ("KRW".equals(fromCurrency)) {
            return requestAmount.divide(rate, 4, RoundingMode.DOWN);
        } else {
            return requestAmount.multiply(rate).setScale(4, RoundingMode.DOWN);
        }
    }

    private void validateCurrencyPair(String fromCurrency, String toCurrency) {
        if (!SUPPORTED_CURRENCIES.contains(fromCurrency) || !SUPPORTED_CURRENCIES.contains(toCurrency)) {
            throw new BusinessException(ExchangeErrorCode.UNSUPPORTED_CURRENCY_PAIR);
        }
        if (fromCurrency.equals(toCurrency)) {
            throw new BusinessException(ExchangeErrorCode.UNSUPPORTED_CURRENCY_PAIR);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ExchangeErrorCode.INVALID_EXCHANGE_AMOUNT);
        }
    }

    private String generateFxOrderNo() {
        return "FX" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private record AccountRound(Account account, SimulationRound round) {}

    private AccountRound resolveAccountAndRound(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
        SimulationRound round = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));
        return new AccountRound(account, round);
    }
}
