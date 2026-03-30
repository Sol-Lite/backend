package com.sollite.user.service;

import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.entity.UserConsent;
import com.sollite.user.domain.repository.UserConsentRepository;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.*;
import com.sollite.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private UserConsentRepository userConsentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EmailService emailService;

    private SignupRequest createSignupRequest() {
        return new SignupRequest(
                "test@example.com", "Test1234!", "Test1234!",
                "홍길동", "010-1234-5678", true, true,
                InvestmentType.BALANCED, "1234"
        );
    }

    private User createUser() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .name("홍길동")
                .phone("01012345678")
                .build();
        user.verifyEmail();
        return user;
    }

    @Nested
    @DisplayName("회원가입 (createUser)")
    class CreateUser {

        @Test
        @DisplayName("회원가입 성공")
        void createUser_success() {
            SignupRequest request = createSignupRequest();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("email_verified:test@example.com")).willReturn("true");
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willAnswer(i -> i.getArgument(0));
            given(userConsentRepository.save(any(UserConsent.class))).willAnswer(i -> i.getArgument(0));

            User user = userService.createUser(request);

            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getName()).isEqualTo("홍길동");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("회원가입 실패 - 이메일 미인증")
        void createUser_fail_emailNotVerified() {
            SignupRequest request = createSignupRequest();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("email_verified:test@example.com")).willReturn(null);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.EMAIL_NOT_VERIFIED));
        }

        @Test
        @DisplayName("회원가입 실패 - 이메일 중복")
        void createUser_fail_duplicateEmail() {
            SignupRequest request = createSignupRequest();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("email_verified:test@example.com")).willReturn("true");
            given(userRepository.existsByEmail("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.DUPLICATE_EMAIL));
        }

        @Test
        @DisplayName("회원가입 실패 - 비밀번호 확인 불일치")
        void createUser_fail_passwordMismatch() {
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Test1234!", "Different1!",
                    "홍길동", null, true, true, InvestmentType.BALANCED, "1234"
            );

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.PASSWORD_CONFIRM_MISMATCH));
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("로그인 성공")
        void login_success() {
            User user = createUser();
            LoginRequest request = new LoginRequest("test@example.com", "Test1234!", false);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Test1234!", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(), eq("test@example.com"))).willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(any(), anyLong())).willReturn("refresh-token");
            given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(1800000L);
            given(jwtTokenProvider.getRefreshTokenExpiry(false)).willReturn(604800000L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            LoginResult result = userService.login(request);

            assertThat(result.response().accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            assertThat(result.response().expiresIn()).isEqualTo(1800);
            assertThat(result.response().user().email()).isEqualTo("test@example.com");
            assertThat(result.response().user().name()).isEqualTo("홍길동");
            verify(valueOperations).set(anyString(), eq("refresh-token"), anyLong(), any());
        }

        @Test
        @DisplayName("로그인 실패 - 존재하지 않는 이메일")
        void login_fail_userNotFound() {
            LoginRequest request = new LoginRequest("unknown@example.com", "Test1234!", false);
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_PASSWORD));
        }

        @Test
        @DisplayName("로그인 실패 - 비밀번호 불일치 시 실패 기록")
        void login_fail_wrongPassword() {
            User user = createUser();
            LoginRequest request = new LoginRequest("test@example.com", "Wrong1234!", false);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Wrong1234!", "encodedPassword")).willReturn(false);
            doAnswer(invocation -> {
                ((User) invocation.getArgument(0)).incrementLoginFailCount();
                return null;
            }).when(loginAttemptService).recordFailure(any(User.class));

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_PASSWORD));

            verify(loginAttemptService).recordFailure(user);
        }

        @Test
        @DisplayName("로그인 실패 - 계정 잠금 상태")
        void login_fail_accountLocked() {
            User user = createUser();
            for (int i = 0; i < 5; i++) user.incrementLoginFailCount();
            LoginRequest request = new LoginRequest("test@example.com", "Test1234!", false);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.ACCOUNT_LOCKED));
        }

        @Test
        @DisplayName("로그인 실패 - 5회 실패로 계정 잠금 발생")
        void login_fail_lockAfter5Attempts() {
            User user = createUser();
            for (int i = 0; i < 4; i++) user.incrementLoginFailCount();
            LoginRequest request = new LoginRequest("test@example.com", "Wrong1234!", false);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Wrong1234!", "encodedPassword")).willReturn(false);
            doAnswer(invocation -> {
                ((User) invocation.getArgument(0)).incrementLoginFailCount();
                return null;
            }).when(loginAttemptService).recordFailure(any(User.class));

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.ACCOUNT_LOCKED));

            assertThat(user.isLocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃 성공")
        void logout_success() {
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);

            userService.logout(1L, "valid-refresh-token", null);

            verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        }

        @Test
        @DisplayName("로그아웃 실패 - 유효하지 않은 리프레시 토큰")
        void logout_fail_invalidToken() {
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-1L);

            assertThatThrownBy(() -> userService.logout(1L, "wrong-token", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("로그아웃 - 이미 로그아웃된 상태는 성공으로 처리 (멱등)")
        void logout_alreadyLoggedOut_isIdempotent() {
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(0L);

            assertThatCode(() -> userService.logout(1L, "some-token", null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class RefreshToken {

        @Test
        @DisplayName("토큰 갱신 성공")
        void refreshToken_success() {
            User user = createUser();
            long remainingTtl = 604800000L;

            given(jwtTokenProvider.validateToken("valid-refresh-token")).willReturn(true);
            given(jwtTokenProvider.getUserIdFromToken("valid-refresh-token")).willReturn(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtTokenProvider.createAccessToken(any(), eq("test@example.com"))).willReturn("new-access-token");
            given(jwtTokenProvider.createRefreshToken(any(), anyLong())).willReturn("new-refresh-token");
            given(redisTemplate.getExpire("refresh:1", TimeUnit.MILLISECONDS)).willReturn(remainingTtl);
            given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(1800000L);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(1L);

            TokenRefreshResult result = userService.refreshToken("valid-refresh-token");

            assertThat(result.response().accessToken()).isEqualTo("new-access-token");
            assertThat(result.response().expiresIn()).isEqualTo(1800);
            assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("토큰 갱신 실패 - 만료된 리프레시 토큰")
        void refreshToken_fail_expired() {
            given(jwtTokenProvider.validateToken("expired-token")).willReturn(false);

            assertThatThrownBy(() -> userService.refreshToken("expired-token"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("토큰 갱신 실패 - Redis에 없는 토큰")
        void refreshToken_fail_notInRedis() {
            given(jwtTokenProvider.validateToken("valid-but-revoked")).willReturn(true);
            given(jwtTokenProvider.getUserIdFromToken("valid-but-revoked")).willReturn(1L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:1")).willReturn(null);

            assertThatThrownBy(() -> userService.refreshToken("valid-but-revoked"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_TOKEN));
        }
    }

    @Nested
    @DisplayName("이메일 인증")
    class EmailVerification {

        @Test
        @DisplayName("이메일 인증 발송 성공")
        void sendVerificationEmail_success() {
            given(userRepository.existsByEmail("test@example.com")).willReturn(false);

            userService.sendVerificationEmail(new EmailVerifyRequest("test@example.com"));

            verify(emailService).sendVerificationEmail(eq("test@example.com"));
        }

        @Test
        @DisplayName("이메일 인증 발송 실패 - 이미 가입된 이메일")
        void sendVerificationEmail_fail_duplicateEmail() {
            given(userRepository.existsByEmail("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> userService.sendVerificationEmail(new EmailVerifyRequest("test@example.com")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.DUPLICATE_EMAIL));
        }

        @Test
        @DisplayName("이메일 인증 확인 성공")
        void confirmEmail_success() {
            userService.confirmEmailVerification(new EmailVerifyConfirmRequest("valid-token"));

            verify(emailService).verifyToken("valid-token");
        }

        @Test
        @DisplayName("이메일 인증 확인 실패 - 만료된 토큰")
        void confirmEmail_fail_expired() {
            doThrow(new BusinessException(UserErrorCode.TOKEN_EXPIRED))
                    .when(emailService).verifyToken("expired-token");

            assertThatThrownBy(() -> userService.confirmEmailVerification(new EmailVerifyConfirmRequest("expired-token")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class PasswordReset {

        @Test
        @DisplayName("비밀번호 재설정 요청 성공 - 등록된 이메일")
        void requestPasswordReset_success() {
            User user = createUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

            userService.requestPasswordReset(new PasswordResetRequest("test@example.com"));

            verify(emailService).sendPasswordResetEmail(eq("test@example.com"), any());
        }

        @Test
        @DisplayName("비밀번호 재설정 요청 - 미등록 이메일도 정상 응답")
        void requestPasswordReset_unknownEmail_noException() {
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            userService.requestPasswordReset(new PasswordResetRequest("unknown@example.com"));

            verify(emailService, never()).sendPasswordResetEmail(any(), any());
        }

        @Test
        @DisplayName("비밀번호 재설정 확인 성공")
        void confirmPasswordReset_success() {
            User user = createUser();
            given(emailService.verifyPasswordResetToken("valid-token"))
                    .willReturn(java.util.Map.of("user_id", "1", "email", "test@example.com"));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("NewPass1!")).willReturn("newEncodedPassword");
            given(redisTemplate.delete("refresh:1")).willReturn(true);

            userService.confirmPasswordReset(new PasswordResetConfirmRequest("valid-token", "NewPass1!", "NewPass1!"));

            assertThat(user.getPasswordHash()).isEqualTo("newEncodedPassword");
            verify(redisTemplate).delete("refresh:1");
        }

        @Test
        @DisplayName("비밀번호 재설정 확인 실패 - 비밀번호 확인 불일치")
        void confirmPasswordReset_fail_mismatch() {
            assertThatThrownBy(() -> userService.confirmPasswordReset(
                    new PasswordResetConfirmRequest("valid-token", "NewPass1!", "Different1!")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.PASSWORD_CONFIRM_MISMATCH));
        }

        @Test
        @DisplayName("비밀번호 재설정 확인 실패 - 만료된 토큰")
        void confirmPasswordReset_fail_expired() {
            given(emailService.verifyPasswordResetToken("expired-token"))
                    .willThrow(new BusinessException(UserErrorCode.TOKEN_EXPIRED));

            assertThatThrownBy(() -> userService.confirmPasswordReset(
                    new PasswordResetConfirmRequest("expired-token", "NewPass1!", "NewPass1!")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("비밀번호 재설정 확인 실패 - 토큰 user_id 형식 오류")
        void confirmPasswordReset_fail_invalidUserId() {
            given(emailService.verifyPasswordResetToken("invalid-token"))
                    .willReturn(java.util.Map.of("user_id", "not-a-number", "email", "test@example.com"));

            assertThatThrownBy(() -> userService.confirmPasswordReset(
                    new PasswordResetConfirmRequest("invalid-token", "NewPass1!", "NewPass1!")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }
    }

    @Nested
    @DisplayName("현재 비밀번호 검증 (verifyPassword)")
    class VerifyPassword {

        @Test
        @DisplayName("비밀번호 검증 성공")
        void verifyPassword_success() {
            User user = createUser();
            given(redisTemplate.execute(any(RedisScript.class), anyList())).willReturn(0L);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Test1234!", "encodedPassword")).willReturn(true);

            assertThatCode(() -> userService.verifyPassword(1L, "Test1234!"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("비밀번호 검증 실패 - 비밀번호 불일치")
        void verifyPassword_fail_wrongPassword() {
            User user = createUser();
            given(redisTemplate.execute(any(RedisScript.class), anyList())).willReturn(0L);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Wrong1234!", "encodedPassword")).willReturn(false);

            assertThatThrownBy(() -> userService.verifyPassword(1L, "Wrong1234!"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_PASSWORD));
        }

        @Test
        @DisplayName("비밀번호 검증 실패 - 존재하지 않는 사용자")
        void verifyPassword_fail_userNotFound() {
            given(redisTemplate.execute(any(RedisScript.class), anyList())).willReturn(0L);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(1L);
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.verifyPassword(99L, "Test1234!"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("비밀번호 검증 실패 - 요청 횟수 초과 (Rate Limit)")
        void verifyPassword_fail_tooManyRequests() {
            given(redisTemplate.execute(any(RedisScript.class), anyList())).willReturn(10L);

            assertThatThrownBy(() -> userService.verifyPassword(1L, "Test1234!"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOO_MANY_REQUESTS));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("비밀번호 변경 성공")
        void changePassword_success() {
            User user = createUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Test1234!", "encodedPassword")).willReturn(true);
            given(passwordEncoder.encode("NewPass1!")).willReturn("newEncodedPassword");

            userService.changePassword(1L, new PasswordChangeRequest("Test1234!", "NewPass1!", "NewPass1!"));

            assertThat(user.getPasswordHash()).isEqualTo("newEncodedPassword");
        }

        @Test
        @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
        void changePassword_fail_wrongCurrent() {
            User user = createUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Wrong1234!", "encodedPassword")).willReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L,
                    new PasswordChangeRequest("Wrong1234!", "NewPass1!", "NewPass1!")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_PASSWORD));
        }

        @Test
        @DisplayName("비밀번호 변경 실패 - 새 비밀번호 확인 불일치")
        void changePassword_fail_mismatch() {
            assertThatThrownBy(() -> userService.changePassword(1L,
                    new PasswordChangeRequest("Test1234!", "NewPass1!", "Different1!")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.PASSWORD_CONFIRM_MISMATCH));
        }
    }

    @Nested
    @DisplayName("프로필")
    class Profile {

        @Test
        @DisplayName("내 정보 조회 성공")
        void getProfile_success() {
            User user = createUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.empty());

            UserProfileResponse response = userService.getProfile(1L);

            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.name()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("내 정보 조회 실패 - 존재하지 않는 사용자")
        void getProfile_fail_notFound() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("내 정보 수정 성공")
        void updateProfile_success() {
            User user = createUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.empty());

            UserProfileResponse response = userService.updateProfile(1L, new ProfileUpdateRequest("김길동", "010-9876-5432"));

            assertThat(response.name()).isEqualTo("김길동");
            assertThat(response.phone()).isEqualTo("0109876-5432".replace("-", ""));
        }
    }
}
