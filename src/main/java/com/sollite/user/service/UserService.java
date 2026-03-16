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

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
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

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_PASSWORD));

        if (user.isLocked()) {
            throw new BusinessException(UserErrorCode.ACCOUNT_LOCKED);
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

        redisTemplate.opsForValue().set(
                "refresh:" + user.getUserId(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpiry(),
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

    public void logout(Long userId, LogoutRequest request) {
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);

        if (storedToken == null || !storedToken.equals(request.refreshToken())) {
            throw new BusinessException(UserErrorCode.INVALID_TOKEN);
        }

        redisTemplate.delete("refresh:" + userId);
    }

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

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getEmail());

        return new TokenRefreshResponse(
                newAccessToken,
                jwtTokenProvider.getAccessTokenExpiry() / 1000
        );
    }

    public void sendVerificationEmail(EmailVerifyRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        emailService.sendVerificationEmail(user.getEmail(), user.getUserId());
    }

    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.email())
                .ifPresent(user -> emailService.sendPasswordResetEmail(user.getEmail(), user.getUserId()));
    }

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
        userRepository.save(user);

        // 모든 세션 강제 로그아웃
        redisTemplate.delete("refresh:" + userId);
    }

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
        userRepository.save(user);
    }

    @Transactional
    public void confirmEmailVerification(EmailVerifyConfirmRequest request) {
        java.util.Map<String, String> tokenData = emailService.verifyToken(request.token());

        Long userId = Long.parseLong(tokenData.get("user_id"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.verifyEmail();
        userRepository.save(user);
    }
}
