package com.sollite.user.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.*;
import com.sollite.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 사용자 인증 및 프로필 관리 비즈니스 로직을 담당하는 서비스 클래스.
 * 회원가입, 로그인/로그아웃, 비밀번호 변경, 프로필 관리, 이메일 인증 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;

    /**
     * 사용자를 신규 등록합니다.
     *
     * @param request 회원가입 정보 (이메일, 비밀번호, 이름, 전화번호, 약관동의 여부)
     * @return 등록된 사용자의 ID, 이메일, 이름과 안내 메시지
     * @throws BusinessException 이메일 중복, 비밀번호 불일치 시
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password()
                .equals(request.passwordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .serviceTermsAgreed(request.serviceTermsAgreed())
                .privacyTermsAgreed(request.privacyTermsAgreed())
                .marketingAgreed(request.marketingAgreed())
                .build();

        userRepository.save(user);

        return new SignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요."
        );
    }

    /**
     * 사용자 로그인을 처리합니다. 실패 시 로그인 시도를 기록하고 5회 실패 시 계정을 잠급니다.
     *
     * @param request 로그인 정보 (이메일, 비밀번호, 자동로그인 여부)
     * @return 접근 토큰, 갱신 토큰, 토큰 유효시간, 사용자 정보
     * @throws BusinessException 계정 미등록, 이메일 미인증, 비밀번호 오류, 계정 잠금 시
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_PASSWORD));

        if (user.isLocked()) {
            throw new BusinessException(UserErrorCode.ACCOUNT_LOCKED);
        }

        if (!user.isEmailVerified()) {
            throw new BusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(user);
            if (user.isLocked()) {
                throw new BusinessException(UserErrorCode.ACCOUNT_LOCKED);
            }
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        loginAttemptService.recordSuccess(user);

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        long refreshTokenExpiry = jwtTokenProvider.getRefreshTokenExpiry(request.isAutoLogin());
        redisTemplate.opsForValue().set(
                "refresh:" + user.getUserId(),
                refreshToken,
                refreshTokenExpiry,
                TimeUnit.MILLISECONDS
        );

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiry() / 1000,
                new LoginResponse.UserInfo(
                        user.getUserId(),
                        user.getEmail(),
                        user.getName()
                )
        );
    }

    /**
     * 사용자 로그아웃을 처리하고 갱신 토큰을 무효화합니다.
     *
     * @param userId 로그아웃할 사용자의 ID
     * @param request 갱신 토큰
     * @throws BusinessException 유효하지 않은 토큰 시
     */
    public void logout(Long userId, LogoutRequest request) {
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);

        if (storedToken == null || !storedToken.equals(request.refreshToken())) {
            throw new BusinessException(UserErrorCode.INVALID_TOKEN);
        }

        redisTemplate.delete("refresh:" + userId);
    }

    /**
     * 만료 가능한 갱신 토큰으로 새로운 접근 토큰을 발급합니다.
     *
     * @param request 갱신 토큰
     * @return 새로운 접근 토큰과 유효시간 (초 단위)
     * @throws BusinessException 토큰 만료, 유효하지 않은 토큰, 사용자 미등록 시
     */
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());

        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);
        if (storedToken == null || !storedToken.equals(request.refreshToken())) {
            throw new BusinessException(UserErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String newAccessToken  = jwtTokenProvider.createAccessToken(user.getUserId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // 기존 토큰의 남은 TTL을 그대로 유지하여 새 토큰 저장
        long remainingTtl = redisTemplate.getExpire("refresh:" + userId, TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(
                "refresh:" + userId,
                newRefreshToken,
                remainingTtl > 0 ? remainingTtl : jwtTokenProvider.getRefreshTokenExpiry(false),
                TimeUnit.MILLISECONDS
        );

        return new TokenRefreshResponse(
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getAccessTokenExpiry() / 1000
        );
    }

    /**
     * 이메일 인증 메일을 발송합니다. 사용자 존재 여부와 무관하게 항상 성공 응답을 반환합니다 (User Enumeration 방지).
     *
     * @param request 이메일 주소
     */
    public void sendVerificationEmail(EmailVerifyRequest request) {
        userRepository.findByEmail(request.email())
                .ifPresent(user -> emailService.sendVerificationEmail(user.getEmail(), user.getUserId()));
    }

    /**
     * 비밀번호 재설정 메일을 요청합니다. 사용자 존재 여부와 무관하게 항상 성공 응답을 반환합니다 (User Enumeration 방지).
     *
     * @param request 이메일 주소
     */
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.email())
                .ifPresent(user -> emailService.sendPasswordResetEmail(user.getEmail(), user.getUserId()));
    }

    /**
     * 비밀번호 재설정 토큰을 검증하고 비밀번호를 변경한 후 모든 세션을 강제 로그아웃시킵니다.
     *
     * @param request 재설정 토큰과 새 비밀번호
     * @throws BusinessException 토큰 만료, 비밀번호 불일치, 사용자 미등록 시
     */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        java.util.Map<String, String> tokenData = emailService.verifyPasswordResetToken(request.token());

        Long userId = Long.parseLong(tokenData.get("user_id"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.changePassword(passwordEncoder.encode(request.newPassword()));

        // 모든 세션 강제 로그아웃
        redisTemplate.delete("refresh:" + userId);
    }

    /**
     * 현재 비밀번호를 검증하고 새 비밀번호로 변경합니다.
     *
     * @param userId 사용자 ID
     * @param request 현재 비밀번호와 새 비밀번호
     * @throws BusinessException 비밀번호 불일치, 사용자 미등록 시
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 사용자의 프로필 정보를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자의 ID, 이메일, 이름, 전화번호, 이메일 인증 여부, 가입일시
     * @throws BusinessException 사용자 미등록 시
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return new UserProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }

    /**
     * 사용자의 프로필 정보를 수정합니다.
     *
     * @param userId 사용자 ID
     * @param request 수정할 이름과 전화번호
     * @return 수정된 프로필 정보
     * @throws BusinessException 사용자 미등록 시
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.updateProfile(request.name(), request.phone());

        return new UserProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }

    /**
     * 이메일 인증 토큰을 검증하고 사용자의 이메일을 인증 처리합니다.
     *
     * @param request 이메일 인증 토큰
     * @throws BusinessException 토큰 만료, 사용자 미등록 시
     */
    @Transactional
    public void confirmEmailVerification(EmailVerifyConfirmRequest request) {
        java.util.Map<String, String> tokenData = emailService.verifyToken(request.token());

        Long userId = Long.parseLong(tokenData.get("user_id"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.verifyEmail();
    }
}
