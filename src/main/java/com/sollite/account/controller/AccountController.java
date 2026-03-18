package com.sollite.account.controller;

import com.sollite.account.dto.AccountCloseRequest;
import com.sollite.account.dto.AccountInfoResponse;
import com.sollite.account.dto.PinChangeRequest;
import com.sollite.account.dto.PinResetConfirmRequest;
import com.sollite.account.dto.PinVerifyRequest;
import com.sollite.account.service.AccountService;
import com.sollite.global.util.AuthUtil;
import com.sollite.user.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * 계좌 비밀번호를 변경합니다. 현재 비밀번호를 알고 있을 때 사용합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 현재 PIN과 새 PIN
     * @return 200 OK - 변경 완료 메시지
     * @throws BusinessException 계좌 미존재, 현재 PIN 불일치 시
     */
    @PatchMapping("/me/pin")
    public ResponseEntity<MessageResponse> changePin(
            Authentication authentication,
            @Valid @RequestBody PinChangeRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        accountService.changePin(userId, request.currentPin(), request.newPin());
        return ResponseEntity.ok(new MessageResponse("계좌 비밀번호가 변경되었습니다."));
    }

    /**
     * 계좌 비밀번호 재설정 메일을 요청합니다. PIN을 분실했을 때 사용합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @return 200 OK - 발송 완료 메시지
     */
    @PostMapping("/pin/reset/request")
    public ResponseEntity<MessageResponse> requestPinReset(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        accountService.requestPinReset(userId);
        return ResponseEntity.ok(new MessageResponse("계좌 비밀번호 재설정 메일이 발송되었습니다."));
    }

    /**
     * 계좌 비밀번호 재설정을 확인하고 처리합니다.
     *
     * @param request 재설정 토큰과 새 PIN
     * @return 200 OK - 변경 완료 메시지
     * @throws BusinessException 토큰 만료, 계좌 미존재 시
     */
    @PostMapping("/pin/reset/confirm")
    public ResponseEntity<MessageResponse> confirmPinReset(
            @Valid @RequestBody PinResetConfirmRequest request) {
        accountService.confirmPinReset(request.token(), request.newPin());
        return ResponseEntity.ok(new MessageResponse("계좌 비밀번호가 재설정되었습니다."));
    }

    /**
     * 계좌를 폐쇄합니다. PIN 인증 필수.
     * 잔고/보유자산/미체결주문 검증은 추후 추가 예정 (issue #15)
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 계좌 비밀번호
     * @return 200 OK - 폐쇄 완료 메시지
     * @throws BusinessException 계좌 미존재, 이미 폐쇄된 계좌, PIN 불일치 시
     */
    @DeleteMapping
    public ResponseEntity<MessageResponse> closeAccount(
            Authentication authentication,
            @Valid @RequestBody AccountCloseRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        accountService.closeAccount(userId, request.accountPin());
        return ResponseEntity.ok(new MessageResponse("계좌가 폐쇄되었습니다."));
    }
}

