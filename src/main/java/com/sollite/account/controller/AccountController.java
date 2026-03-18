package com.sollite.account.controller;

import com.sollite.account.dto.AccountInfoResponse;
import com.sollite.account.dto.PinVerifyRequest;
import com.sollite.account.service.AccountService;
import com.sollite.global.util.AuthUtil;
import com.sollite.user.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * 계좌 비밀번호를 인증합니다. 매수/매도 등 민감한 액션 전 호출합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 계좌 비밀번호 (4자리 숫자)
     * @return 200 OK - 인증 성공 메시지
     * @throws BusinessException 계좌 미존재, 비밀번호 불일치 시
     */
    @PostMapping("/verify-pin")
    public ResponseEntity<MessageResponse> verifyPin(
            Authentication authentication,
            @Valid @RequestBody PinVerifyRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        accountService.verifyPin(userId, request.accountPin());
        return ResponseEntity.ok(new MessageResponse("계좌 비밀번호가 확인되었습니다."));
    }
}

