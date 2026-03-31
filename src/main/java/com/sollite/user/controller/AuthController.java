package com.sollite.user.controller;

import com.sollite.global.exception.BusinessException;
import com.sollite.global.util.AuthUtil;
import com.sollite.user.dto.*;
import com.sollite.user.exception.UserErrorCode;
import com.sollite.user.service.SignupFacade;
import com.sollite.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;


/**
 * 사용자 인증 관련 API 컨트롤러.
 * 회원가입, 로그인, 로그아웃, 토큰 갱신, 이메일 인증, 비밀번호 재설정 등을 처리합니다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final SignupFacade signupFacade;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    /**
     * 회원가입을 처리합니다.
     *
     * @param request 회원가입 정보 (이메일, 비밀번호, 이름, 전화번호 등)
     * @return 201 Created - 생성된 사용자 정보와 메시지
     * @throws BusinessException 이메일 중복, 유효성 검증 실패 시
     */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupFacade.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그인을 처리합니다.
     *
     * @param request 로그인 정보 (이메일, 비밀번호, 자동로그인 여부)
     * @return 200 OK - 접근 토큰, 사용자 정보 (refresh token은 HttpOnly 쿠키로 전달)
     * @throws BusinessException 계정 미등록, 비밀번호 오류, 계정 잠금 시
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        LoginResult result = userService.login(request);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(
                result.refreshToken(),
                result.refreshTokenMaxAge(),
                httpRequest
        ).toString());
        return ResponseEntity.ok(result.response());
    }

    /**
     * 이메일 인증 메일을 발송합니다.
     *
     * @param request 이메일 인증 요청 (이메일 주소)
     * @return 200 OK - 발송 완료 메시지와 토큰 유효시간 (초 단위)
     * @throws BusinessException 사용자 미등록, 재발송 제한 초과 시
     */
    @PostMapping("/email/verify/send")
    public ResponseEntity<EmailVerifyResponse> sendVerificationEmail(@Valid @RequestBody EmailVerifyRequest request) {
        String token = userService.sendVerificationEmail(request);
        return ResponseEntity.ok(new EmailVerifyResponse(
                "인증 메일이 발송되었습니다.",
                600,
                token
        ));
    }

    /**
     * 이메일 인증을 확인하고 처리합니다.
     *
     * @param request 이메일 인증 토큰
     * @return 200 OK - 인증 완료 메시지
     * @throws BusinessException 토큰 만료, 유효하지 않은 토큰 시
     */
    @PostMapping("/email/verify/confirm")
    public ResponseEntity<MessageResponse> confirmEmailVerification(@Valid @RequestBody EmailVerifyConfirmRequest request) {
        userService.confirmEmailVerification(request);
        return ResponseEntity.ok(new MessageResponse("이메일 인증이 완료되었습니다."));
    }

    /**
     * 이메일 인증 완료 여부를 조회합니다. 회원가입 페이지에서 폴링용으로 사용합니다.
     *
     * @param email 이메일 주소
     * @return 200 OK - 인증 완료 여부
     */
    @GetMapping("/email/verify/status")
    public ResponseEntity<EmailVerifyStatusResponse> checkEmailVerifyStatus(@RequestParam String token) {
        boolean verified = userService.checkEmailVerifiedByToken(token);
        return ResponseEntity.ok(new EmailVerifyStatusResponse(verified));
    }

    /**
     * 비밀번호 재설정 메일을 요청합니다.
     *
     * @param request 비밀번호 재설정 요청 (이메일 주소)
     * @return 200 OK - 발송 완료 메시지 (사용자 존재 여부와 무관하게 동일 응답)
     * @throws BusinessException 재발송 제한 초과 시
     */
    @PostMapping("/password/reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        userService.requestPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("비밀번호 재설정 메일이 발송되었습니다."));
    }

    /**
     * 비밀번호 재설정을 확인하고 처리합니다.
     *
     * @param request 비밀번호 재설정 토큰과 새 비밀번호
     * @return 200 OK - 변경 완료 메시지
     * @throws BusinessException 토큰 만료, 비밀번호 불일치 시
     */
    @PostMapping("/password/reset/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.confirmPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("비밀번호가 변경되었습니다. 다시 로그인해주세요."));
    }

    /**
     * JWT 접근 토큰을 갱신합니다.
     *
     * @param request 갱신 토큰
     * @return 200 OK - 새로운 접근 토큰과 유효시간 (refresh token은 HttpOnly 쿠키로 갱신)
     * @throws BusinessException 토큰 만료, 유효하지 않은 토큰 시
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (refreshToken == null) {
            throw new BusinessException(UserErrorCode.INVALID_TOKEN);
        }
        TokenRefreshResult result = userService.refreshToken(refreshToken);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(
                result.refreshToken(),
                result.refreshTokenMaxAge(),
                httpRequest
        ).toString());
        return ResponseEntity.ok(result.response());
    }

    /**
     * 로그아웃을 처리합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 갱신 토큰 (로그아웃 시 무효화)
     * @return 200 OK - 로그아웃 완료 메시지
     * @throws BusinessException 유효하지 않은 토큰 시
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(Authentication authentication,
                                                   @CookieValue(name = "refreshToken", required = false) String refreshToken,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        Long userId = AuthUtil.getUserId(authentication);
        String accessToken = resolveToken(httpRequest);
        userService.logout(userId, refreshToken != null ? refreshToken : "", accessToken);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie("", 0, httpRequest).toString());
        return ResponseEntity.ok(new MessageResponse("로그아웃 되었습니다."));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private ResponseCookie buildRefreshTokenCookie(String value, long maxAgeMillis, HttpServletRequest request) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(shouldUseSecureCookie(request))
                .path("/api/auth")
                .maxAge(maxAgeMillis / 1000)
                .sameSite("Lax")
                .build();
    }

    private boolean shouldUseSecureCookie(HttpServletRequest request) {
        if (!cookieSecure) {
            return false;
        }
        if (request == null) {
            return true;
        }
        if (request.isSecure()) {
            return true;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && "https".equalsIgnoreCase(forwardedProto)) {
            return true;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && origin.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return true;
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer != null && referer.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return true;
        }

        return !isLocalhost(request.getServerName());
    }

    private boolean isLocalhost(String serverName) {
        if (serverName == null) {
            return false;
        }
        String normalized = serverName.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]")
                || normalized.equals("0:0:0:0:0:0:0:1");
    }
}
