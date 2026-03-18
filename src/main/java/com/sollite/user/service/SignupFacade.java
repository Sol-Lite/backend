package com.sollite.user.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.service.AccountService;
import com.sollite.user.domain.entity.User;
import com.sollite.user.dto.SignupRequest;
import com.sollite.user.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 + 계좌 개설을 단일 트랜잭션으로 처리하는 Facade.
 * User 생성(UserService)과 Account 생성(AccountService)을 조율합니다.
 */
@Service
@RequiredArgsConstructor
public class SignupFacade {

    private final UserService userService;
    private final AccountService accountService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        User user = userService.createUser(request);
        Account account = accountService.createAccount(user, request.investmentType(), request.accountPin());

        return new SignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                account.getAccountId(),
                account.getAccountNo(),
                "회원가입이 완료되었습니다."
        );
    }
}
