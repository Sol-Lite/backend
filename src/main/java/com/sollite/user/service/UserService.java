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

}
