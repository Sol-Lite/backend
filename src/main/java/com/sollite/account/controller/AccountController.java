package com.sollite.account.controller;

import com.sollite.account.dto.AccountInfoResponse;
import com.sollite.account.service.AccountService;
import com.sollite.global.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * 현재 로그인한 사용자의 계좌 정보를 조회합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @return 200 OK - 계좌 ID, 계좌번호, 계좌명, 투자성향, 계좌상태, 개설일
     */
    @GetMapping("/me")
    public ResponseEntity<AccountInfoResponse> getMyAccount(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(accountService.getMyAccount(userId));
    }

}

