package com.sollite.account.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.dto.AccountInfoResponse;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.global.exception.BusinessException;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.exception.UserErrorCode;
import com.sollite.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public void requestPinReset(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        emailService.sendPinResetEmail(user.getEmail(), userId);
    }

    @Transactional
    public void confirmPinReset(String token, String newPin) {
        java.util.Map<String, String> tokenData = emailService.verifyPinResetToken(token);

        Long userId = Long.parseLong(tokenData.get("user_id"));
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        account.changePin(passwordEncoder.encode(newPin));
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
}
