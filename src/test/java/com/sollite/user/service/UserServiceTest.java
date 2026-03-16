package com.sollite.user.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.user.domain.entity.User;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private EmailService emailService;

    private SignupRequest createSignupRequest() {
        return new SignupRequest(
                "test@example.com",
                "Test1234!",
                "Test1234!",
                "홍길동",
                "010-1234-5678",
                true,
                true,
                false
        );
    }

    private User createUser() {
        return User.builder()
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .name("홍길동")
                .phone("010-1234-5678")
                .serviceTermsAgreed(true)
                .privacyTermsAgreed(true)
                .marketingAgreed(false)
                .build();
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("회원가입 성공")
        void signup_success() {
            SignupRequest request = createSignupRequest();
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            SignupResponse response = userService.signup(request);

            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(response.message()).contains("회원가입이 완료되었습니다");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("회원가입 실패 - 이메일 중복")
        void signup_fail_duplicateEmail() {
            SignupRequest request = createSignupRequest();
            given(userRepository.existsByEmail("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.DUPLICATE_EMAIL));
        }

        @Test
        @DisplayName("회원가입 실패 - 비밀번호 확인 불일치")
        void signup_fail_passwordMismatch() {
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Test1234!", "Different1!",
                    "홍길동", null, true, true, false
            );

            assertThatThrownBy(() -> userService.signup(request))
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
            given(jwtTokenProvider.createRefreshToken(any())).willReturn("refresh-token");
            given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(1800000L);
            given(jwtTokenProvider.getRefreshTokenExpiry()).willReturn(604800000L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            LoginResponse response = userService.login(request);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.expiresIn()).isEqualTo(1800);
            assertThat(response.user().email()).isEqualTo("test@example.com");
            assertThat(response.user().name()).isEqualTo("홍길동");
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
            // 5번 실패시켜서 잠금
            for (int i = 0; i < 5; i++) {
                user.incrementLoginFailCount();
            }
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
            // 이미 4번 실패
            for (int i = 0; i < 4; i++) {
                user.incrementLoginFailCount();
            }
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
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:1")).willReturn("valid-refresh-token");

            LogoutRequest request = new LogoutRequest("valid-refresh-token");
            userService.logout(1L, request);

            verify(redisTemplate).delete("refresh:1");
        }

        @Test
        @DisplayName("로그아웃 실패 - 유효하지 않은 리프레시 토큰")
        void logout_fail_invalidToken() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:1")).willReturn("stored-token");

            LogoutRequest request = new LogoutRequest("wrong-token");

            assertThatThrownBy(() -> userService.logout(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("로그아웃 실패 - 이미 로그아웃된 상태")
        void logout_fail_alreadyLoggedOut() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:1")).willReturn(null);

            LogoutRequest request = new LogoutRequest("some-token");

            assertThatThrownBy(() -> userService.logout(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_TOKEN));
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class RefreshToken {

        @Test
        @DisplayName("토큰 갱신 성공")
        void refreshToken_success() {
            User user = createUser();
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");

            given(jwtTokenProvider.validateToken("valid-refresh-token")).willReturn(true);
            given(jwtTokenProvider.getUserIdFromToken("valid-refresh-token")).willReturn(1L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:1")).willReturn("valid-refresh-token");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtTokenProvider.createAccessToken(any(), eq("test@example.com"))).willReturn("new-access-token");
            given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(1800000L);

            TokenRefreshResponse response = userService.refreshToken(request);

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.expiresIn()).isEqualTo(1800);
        }

        @Test
        @DisplayName("토큰 갱신 실패 - 만료된 리프레시 토큰")
        void refreshToken_fail_expired() {
            TokenRefreshRequest request = new TokenRefreshRequest("expired-token");
            given(jwtTokenProvider.validateToken("expired-token")).willReturn(false);

            assertThatThrownBy(() -> userService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("토큰 갱신 실패 - Redis에 없는 토큰")
        void refreshToken_fail_notInRedis() {
            TokenRefreshRequest request = new TokenRefreshRequest("valid-but-revoked");

            given(jwtTokenProvider.validateToken("valid-but-revoked")).willReturn(true);
            given(jwtTokenProvider.getUserIdFromToken("valid-but-revoked")).willReturn(1L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:1")).willReturn(null);

            assertThatThrownBy(() -> userService.refreshToken(request))
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
            User user = createUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

            userService.sendVerificationEmail(new EmailVerifyRequest("test@example.com"));

            verify(emailService).sendVerificationEmail(eq("test@example.com"), any());
        }

        @Test
        @DisplayName("이메일 인증 발송 실패 - 미등록 이메일")
        void sendVerificationEmail_fail_notFound() {
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.sendVerificationEmail(new EmailVerifyRequest("unknown@example.com")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("이메일 인증 확인 성공")
        void confirmEmail_success() {
            User user = createUser();
            given(emailService.verifyToken("valid-token"))
                    .willReturn(java.util.Map.of("user_id", "1", "email", "test@example.com"));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            userService.confirmEmailVerification(new EmailVerifyConfirmRequest("valid-token"));

            assertThat(user.isEmailVerified()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("이메일 인증 확인 실패 - 만료된 토큰")
        void confirmEmail_fail_expired() {
            given(emailService.verifyToken("expired-token"))
                    .willThrow(new BusinessException(UserErrorCode.TOKEN_EXPIRED));

            assertThatThrownBy(() -> userService.confirmEmailVerification(new EmailVerifyConfirmRequest("expired-token")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }
    }
}
