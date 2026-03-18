package com.sollite.account.controller;

import com.sollite.account.dto.AccountOpenRequest;
import com.sollite.account.dto.AccountOpenResponse;
import com.sollite.account.service.AccountService;
import com.sollite.global.util.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * 계좌를 개설합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 계좌 개설 정보 (이름, 전화번호, 투자성향, 계좌 비밀번호)
     * @return 201 Created - 계좌 ID, 계좌번호, 시드머니, 메시지
     */
    @PostMapping
    public ResponseEntity<AccountOpenResponse> openAccount(
            Authentication authentication,
            @Valid @RequestBody AccountOpenRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        AccountOpenResponse response = accountService.openAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
