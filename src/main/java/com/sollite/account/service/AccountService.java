package com.sollite.account.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.dto.AccountOpenRequest;
import com.sollite.account.dto.AccountOpenResponse;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.global.exception.BusinessException;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.exception.UserErrorCode;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountOpenResponse openAccount(Long userId, AccountOpenRequest request) {
        if (accountRepository.existsByUser_UserId(userId)) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String accountNo = generateAccountNo();
        String pinHash = passwordEncoder.encode(request.accountPin());

        Account account = Account.builder()
                .user(user)
                .accountNo(accountNo)
                .accountName("종합계좌 " + user.getName())
                .accountPinHash(pinHash)
                .investmentTendency(request.investmentType())
                .build();

        accountRepository.save(account);

        SimulationRound round = SimulationRound.builder()
                .account(account)
                .roundNo(1)
                .initialSeedAmount(SEED_MONEY)
                .build();

        simulationRoundRepository.save(round);

        return new AccountOpenResponse(
                account.getAccountId(),
                account.getAccountNo(),
                SEED_MONEY,
                "계좌가 개설되었습니다."
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
