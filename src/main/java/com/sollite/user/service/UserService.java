package com.sollite.user.service;

import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.entity.UserConsent;
import com.sollite.user.domain.repository.UserConsentRepository;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.*;
import com.sollite.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 사용자 인증 및 프로필 관리 비즈니스 로직을 담당하는 서비스 클래스.
 * 회원가입, 로그인/로그아웃, 비밀번호 변경, 프로필 관리, 이메일 인증 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;

    // 검증 + 삭제 원자 처리: 1=성공, 0=이미 없음(멱등), -1=토큰 불일치
    private static final RedisScript<Long> LOGOUT_SCRIPT = RedisScript.of(
            "local s = redis.call('GET', KEYS[1]) " +
            "if s == false then return 0 end " +
            "if s ~= ARGV[1] then return -1 end " +
            "redis.call('DEL', KEYS[1]) " +
            "return 1",
            Long.class);

    // 검증 + 갱신 원자 처리: 1=성공, 0=없음, -1=토큰 불일치
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(
            "local s = redis.call('GET', KEYS[1]) " +
            "if s == false then return 0 end " +
            "if s ~= ARGV[1] then return -1 end " +
            "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]) " +
            "return 1",
            Long.class);

    /**
     * 사용자를 신규 등록합니다. SignupFacade에서 트랜잭션 내에 호출됩니다.
     *
     * @param request 회원가입 정보 (이메일, 비밀번호, 이름, 전화번호, 약관동의 여부)
     * @return 생성된 User 엔티티
     * @throws BusinessException 이메일 중복, 비밀번호 불일치, 이메일 미인증 시
     */
    public User createUser(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        if (!checkEmailVerified(request.email())) {
            throw new BusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone() != null ? request.phone().replaceAll("-", "") : null)
                .build();

        user.verifyEmail();
        userRepository.save(user);

        userConsentRepository.save(UserConsent.of(user, request.serviceTermsAgreed(), request.privacyTermsAgreed()));

        // 사용한 인증 키 삭제
        redisTemplate.delete("email_verified:" + request.email());

        return user;
    }

    /**
     * 사용자 로그인을 처리합니다. 실패 시 로그인 시도를 기록하고 5회 실패 시 계정을 잠급니다.
     *
     * @param request 로그인 정보 (이메일, 비밀번호, 자동로그인 여부)
     * @return 응답 바디와 refresh token 쿠키 생성을 위한 내부 결과
     * @throws BusinessException 계정 미등록, 이메일 미인증, 비밀번호 오류, 계정 잠금 시
     */
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_PASSWORD));

        if (!user.isActive()) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        loginAttemptService.unlockIfExpired(user);

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
        long refreshTokenExpiry = jwtTokenProvider.getRefreshTokenExpiry(request.isAutoLogin());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), refreshTokenExpiry);
        redisTemplate.opsForValue().set(
                "refresh:" + user.getUserId(),
                refreshToken,
                refreshTokenExpiry,
                TimeUnit.MILLISECONDS
        );

        return new LoginResult(
                new LoginResponse(
                        accessToken,
                        jwtTokenProvider.getAccessTokenExpiry() / 1000,
                        new LoginResponse.UserInfo(
                                user.getUserId(),
                                user.getEmail(),
                                user.getName()
                        )
                ),
                refreshToken,
                refreshTokenExpiry
        );
    }

    /**
     * 사용자 로그아웃을 처리하고 갱신 토큰을 무효화합니다.
     *
     * @param userId 로그아웃할 사용자의 ID
     * @param request 갱신 토큰
     * @throws BusinessException 유효하지 않은 토큰 시
     */
    public void logout(Long userId, String refreshToken) {
        Long result = redisTemplate.execute(
                LOGOUT_SCRIPT,
                List.of("refresh:" + userId),
                refreshToken);

        // 0 = 이미 세션 없음 → 멱등 처리 (중복 로그아웃 요청은 성공으로 간주)
        if (result != null && result == -1L) {
            throw new BusinessException(UserErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 만료 가능한 갱신 토큰으로 새로운 접근 토큰을 발급합니다.
     *
     * @param request 갱신 토큰
     * @return 응답 바디와 refresh token 쿠키 갱신을 위한 내부 결과
     * @throws BusinessException 토큰 만료, 유효하지 않은 토큰, 사용자 미등록 시
     */
    public TokenRefreshResult refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (user.isLocked()) {
            throw new BusinessException(UserErrorCode.ACCOUNT_LOCKED);
        }
        if (!user.isActive()) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getEmail());

        long remainingTtl = redisTemplate.getExpire("refresh:" + userId, TimeUnit.MILLISECONDS);
        if (remainingTtl <= 0) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), remainingTtl);

        // 검증 + 갱신 원자 처리
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of("refresh:" + userId),
                refreshToken, newRefreshToken, String.valueOf(remainingTtl));

        if (result == null || result == 0L) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
        if (result == -1L) {
            throw new BusinessException(UserErrorCode.INVALID_TOKEN);
        }

        return new TokenRefreshResult(
                new TokenRefreshResponse(
                        newAccessToken,
                        jwtTokenProvider.getAccessTokenExpiry() / 1000
                ),
                newRefreshToken,
                remainingTtl
        );
    }

    /**
     * 이메일 인증 메일을 발송합니다. 이미 가입된 이메일이면 즉시 오류를 반환합니다.
     *
     * @param request 이메일 주소
     * @throws BusinessException 이미 가입된 이메일인 경우
     */
    public String sendVerificationEmail(EmailVerifyRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        return emailService.sendVerificationEmail(request.email());
    }

    /**
     * 이메일 인증 완료 여부를 확인합니다. 프론트에서 폴링용으로 사용합니다.
     *
     * @param email 이메일 주소
     * @return 인증 완료 여부
     */
    public boolean checkEmailVerified(String email) {
        return "true".equals(redisTemplate.opsForValue().get("email_verified:" + email));
    }

    // 폴링용: token 기반으로 인증 완료 여부 확인
    // email_verify:{token} 키가 사라지면 인증 완료 (링크 클릭 시 삭제됨)
    public boolean checkEmailVerifiedByToken(String token) {
        return !Boolean.TRUE.equals(redisTemplate.hasKey("email_verify:" + token));
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

        Long userId = parseTokenUserId(tokenData);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.unlock();

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
        redisTemplate.delete("refresh:" + userId);
    }

    private Long parseTokenUserId(java.util.Map<String, String> tokenData) {
        String rawUserId = tokenData.get("user_id");
        if (rawUserId == null) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }

        try {
            return Long.parseLong(rawUserId);
        } catch (NumberFormatException e) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
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

        Long accountId = accountRepository.findByUser_UserId(userId)
                .map(account -> account.getAccountId())
                .orElse(null);

        return new UserProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.isEmailVerified(),
                accountId,
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

        String phone = request.phone() != null ? request.phone().replaceAll("-", "") : null;
        user.updateProfile(request.name(), phone);

        Long accountId = accountRepository.findByUser_UserId(userId)
                .map(account -> account.getAccountId())
                .orElse(null);

        return new UserProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.isEmailVerified(),
                accountId,
                user.getCreatedAt()
        );
    }

    /**
     * 이메일 인증 토큰을 검증합니다. 인증 완료 상태는 Redis에 저장되며 회원가입 시 검증됩니다.
     *
     * @param request 이메일 인증 토큰
     * @throws BusinessException 토큰 만료 시
     */
    public void confirmEmailVerification(EmailVerifyConfirmRequest request) {
        emailService.verifyToken(request.token());
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        user.withdraw();
        redisTemplate.delete("refresh:" + userId);
    }
}
