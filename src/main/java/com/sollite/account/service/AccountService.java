package com.sollite.account.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.account.domain.enums.RoundEndReasonCode;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.dto.AccountInfoResponse;
import com.sollite.account.dto.AccountResetResponse;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.balance.domain.entity.CashBalance;
import com.sollite.balance.domain.repository.CashBalanceRepository;
import com.sollite.balance.domain.repository.HoldingRepository;
import com.sollite.balance.exception.BalanceErrorCode;
import com.sollite.global.exception.BusinessException;
import com.sollite.order.domain.enums.OrderStatus;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.exception.UserErrorCode;
import com.sollite.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final BigDecimal SEED_MONEY = new BigDecimal("100000000");

    private final AccountRepository accountRepository;
    private final SimulationRoundRepository simulationRoundRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final CashBalanceRepository cashBalanceRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;

    /**
     * Facade에서 호출하는 계좌 생성 메서드. 트랜잭션은 Facade가 관리합니다.
     */
    public Account createAccount(User user, InvestmentType investmentType, String accountPin) {
        String accountNo = generateAccountNo();
        String pinHash = passwordEncoder.encode(accountPin);

        Account account = Account.builder()
                .user(user)
                .accountNo(accountNo)
                .accountName("종합계좌 " + user.getName())
                .accountPinHash(pinHash)
                .investmentTendency(investmentType)
                .build();

        accountRepository.save(account);

        SimulationRound round = SimulationRound.builder()
                .account(account)
                .roundNo(1)
                .initialSeedAmount(SEED_MONEY)
                .build();

        simulationRoundRepository.save(round);

        // 초기 현금 잔고 1억원 생성
        cashBalanceRepository.save(CashBalance.builder()
                .account(account)
                .simulationRound(round)
                .currencyCode("KRW")
                .availableAmount(SEED_MONEY)
                .totalAmount(SEED_MONEY)
                .build());

        return account;
    }

    @Transactional(readOnly = true)
    public void verifyPin(Long userId, String accountPin) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!passwordEncoder.matches(accountPin, account.getAccountPinHash())) {
            throw new BusinessException(AccountErrorCode.INVALID_PIN);
        }
    }

    @Transactional(readOnly = true)
    public AccountInfoResponse getMyAccount(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return new AccountInfoResponse(
                account.getAccountId(),
                account.getAccountNo(),
                account.getAccountName(),
                account.getInvestmentTendency(),
                account.getAccountStatus().name(),
                account.getCreatedAt()
        );
    }

    @Transactional
    public void changePin(Long userId, String currentPin, String newPin) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!passwordEncoder.matches(currentPin, account.getAccountPinHash())) {
            throw new BusinessException(AccountErrorCode.INVALID_PIN);
        }

        account.changePin(passwordEncoder.encode(newPin));
    }

    @Transactional(readOnly = true)
    public void requestPinReset(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        emailService.sendPinResetEmail(user.getEmail(), userId);
    }

    @Transactional
    public void confirmPinReset(String token, String newPin) {
        java.util.Map<String, String> tokenData = emailService.verifyPinResetToken(token);

        Long userId = parseTokenUserId(tokenData);
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        account.changePin(passwordEncoder.encode(newPin));
    }

    @Transactional
    public AccountResetResponse resetAccount(Long userId, String accountPin) {
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (!passwordEncoder.matches(accountPin, account.getAccountPinHash())) {
            throw new BusinessException(AccountErrorCode.INVALID_PIN);
        }

        SimulationRound currentRound = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

        currentRound.close(RoundEndReasonCode.RESET);

        SimulationRound newRound = SimulationRound.builder()
                .account(account)
                .roundNo(currentRound.getRoundNo() + 1)
                .initialSeedAmount(SEED_MONEY)
                .build();

        simulationRoundRepository.save(newRound);

        // 새 라운드 현금 잔고 1억원으로 초기화
        cashBalanceRepository.save(CashBalance.builder()
                .account(account)
                .simulationRound(newRound)
                .currencyCode("KRW")
                .availableAmount(SEED_MONEY)
                .totalAmount(SEED_MONEY)
                .build());

        return new AccountResetResponse("시뮬레이션이 초기화되었습니다.", newRound.getRoundNo());
    }

    @Transactional
    public void closeAccount(Long userId, String accountPin) {
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (!passwordEncoder.matches(accountPin, account.getAccountPinHash())) {
            throw new BusinessException(AccountErrorCode.INVALID_PIN);
        }

        SimulationRound currentRound = simulationRoundRepository
                .findByAccount_AccountIdAndRoundStatus(account.getAccountId(), RoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACTIVE_ROUND_NOT_FOUND));

        // 잔고 > 0 이면 폐쇄 불가
        CashBalance cb = cashBalanceRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndCurrencyCode(
                        account.getAccountId(), currentRound.getSimulationRoundId(), "KRW")
                .orElseThrow(() -> new BusinessException(BalanceErrorCode.CASH_BALANCE_NOT_FOUND));
        if (cb.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_CLOSE_BALANCE_REMAINS);
        }

        // 보유 주식 있으면 폐쇄 불가
        boolean hasHoldings = holdingRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundId(
                        account.getAccountId(), currentRound.getSimulationRoundId())
                .stream().anyMatch(h -> h.getHoldingQuantity() > 0);
        if (hasHoldings) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_CLOSE_HOLDINGS_REMAIN);
        }

        // 미체결 주문 있으면 폐쇄 불가
        boolean hasPendingOrders = !orderRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndOrderStatusIn(
                        account.getAccountId(), currentRound.getSimulationRoundId(), List.of(OrderStatus.PENDING))
                .isEmpty();
        if (hasPendingOrders) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_CLOSE_PENDING_ORDER_EXISTS);
        }

        currentRound.close(RoundEndReasonCode.ACCOUNT_CLOSED);
        account.close();
    }

    private String generateAccountNo() {
        int seq = ThreadLocalRandom.current().nextInt(100000, 1000000);
        String candidate = "270-86-" + seq;

        while (accountRepository.existsByAccountNo(candidate)) {
            seq = ThreadLocalRandom.current().nextInt(100000, 1000000);
            candidate = "270-86-" + seq;
        }
        return candidate;
    }

    private Long parseTokenUserId(java.util.Map<String, String> tokenData) {
        String rawUserId = tokenData.get("user_id");
        if (rawUserId == null) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }

        try {
            return Long.parseLong(rawUserId);
        } catch (NumberFormatException e) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
    }
}
