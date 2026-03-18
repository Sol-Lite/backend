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
