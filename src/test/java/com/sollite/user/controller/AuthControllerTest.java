package com.sollite.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.exception.GlobalExceptionHandler;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.user.dto.*;
import com.sollite.user.exception.UserErrorCode;
import com.sollite.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("회원가입 API")
    class Signup {

        @Test
        @DisplayName("회원가입 API 성공 - 201 Created")
        @WithMockUser
        void signup_success() throws Exception {
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Test1234!", "Test1234!",
                    "홍길동", "010-1234-5678", true, true, false
            );
            SignupResponse response = new SignupResponse(
                    1L, "test@example.com", "홍길동",
                    "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요."
            );
            given(userService.signup(any(SignupRequest.class))).willReturn(response);

            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("회원가입 API 실패 - 이메일 중복 409")
        @WithMockUser
        void signup_fail_duplicateEmail() throws Exception {
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Test1234!", "Test1234!",
                    "홍길동", null, true, true, false
            );
            given(userService.signup(any(SignupRequest.class)))
                    .willThrow(new BusinessException(UserErrorCode.DUPLICATE_EMAIL));

            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                    .andExpect(jsonPath("$.message").value("이미 등록된 이메일입니다"));
        }

        @Test
        @DisplayName("회원가입 API 실패 - 유효성 검증 400")
        @WithMockUser
        void signup_fail_validation() throws Exception {
            SignupRequest request = new SignupRequest(
                    "", "short", "short",
                    "", null, false, false, false
            );

            mockMvc.perform(post("/api/auth/signup")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors").exists());
        }
    }

    @Nested
    @DisplayName("로그인 API")
    class Login {

        @Test
        @DisplayName("로그인 API 성공 - 200 OK")
        @WithMockUser
        void login_success() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "Test1234!", false);
            LoginResponse response = new LoginResponse(
                    "access-token", "refresh-token", 1800,
                    new LoginResponse.UserInfo(1L, "test@example.com", "홍길동")
            );
            given(userService.login(any(LoginRequest.class))).willReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.expiresIn").value(1800))
                    .andExpect(jsonPath("$.user.email").value("test@example.com"))
                    .andExpect(jsonPath("$.user.name").value("홍길동"));
        }

        @Test
        @DisplayName("로그인 API 실패 - 비밀번호 불일치 401")
        @WithMockUser
        void login_fail_invalidPassword() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "Wrong1234!", false);
            given(userService.login(any(LoginRequest.class)))
                    .willThrow(new BusinessException(UserErrorCode.INVALID_PASSWORD));

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        }

        @Test
        @DisplayName("로그인 API 실패 - 계정 잠금 423")
        @WithMockUser
        void login_fail_accountLocked() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "Test1234!", false);
            given(userService.login(any(LoginRequest.class)))
                    .willThrow(new BusinessException(UserErrorCode.ACCOUNT_LOCKED));

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        }

        @Test
        @DisplayName("로그인 API 실패 - 유효성 검증 400")
        @WithMockUser
        void login_fail_validation() throws Exception {
            LoginRequest request = new LoginRequest("", "", null);

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("로그아웃 API")
    class Logout {

        @Test
        @DisplayName("로그아웃 API 성공 - 200 OK")
        @WithMockUser(username = "1")
        void logout_success() throws Exception {
            LogoutRequest request = new LogoutRequest("valid-refresh-token");
            doNothing().when(userService).logout(any(), any(LogoutRequest.class));

            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("로그아웃 되었습니다."));
        }

        @Test
        @DisplayName("로그아웃 API 실패 - 유효하지 않은 토큰 401")
        @WithMockUser(username = "1")
        void logout_fail_invalidToken() throws Exception {
            LogoutRequest request = new LogoutRequest("invalid-token");
            doThrow(new BusinessException(UserErrorCode.INVALID_TOKEN))
                    .when(userService).logout(any(), any(LogoutRequest.class));

            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
    }

    @Nested
    @DisplayName("토큰 갱신 API")
    class RefreshToken {

        @Test
        @DisplayName("토큰 갱신 API 성공 - 200 OK")
        @WithMockUser
        void refreshToken_success() throws Exception {
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            TokenRefreshResponse response = new TokenRefreshResponse("new-access-token", 1800);
            given(userService.refreshToken(any(TokenRefreshRequest.class))).willReturn(response);

            mockMvc.perform(post("/api/auth/token/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                    .andExpect(jsonPath("$.expiresIn").value(1800));
        }

        @Test
        @DisplayName("토큰 갱신 API 실패 - 만료된 토큰 401")
        @WithMockUser
        void refreshToken_fail_expired() throws Exception {
            TokenRefreshRequest request = new TokenRefreshRequest("expired-token");
            given(userService.refreshToken(any(TokenRefreshRequest.class)))
                    .willThrow(new BusinessException(UserErrorCode.TOKEN_EXPIRED));

            mockMvc.perform(post("/api/auth/token/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }
    }

    @Nested
    @DisplayName("이메일 인증 발송 API")
    class EmailVerifySend {

        @Test
        @DisplayName("이메일 인증 발송 성공 - 200 OK")
        @WithMockUser
        void send_success() throws Exception {
            EmailVerifyRequest request = new EmailVerifyRequest("test@example.com");
            doNothing().when(userService).sendVerificationEmail(any(EmailVerifyRequest.class));

            mockMvc.perform(post("/api/auth/email/verify/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("인증 메일이 발송되었습니다."))
                    .andExpect(jsonPath("$.expiresIn").value(1800));
        }

        @Test
        @DisplayName("이메일 인증 발송 실패 - 미등록 이메일 404")
        @WithMockUser
        void send_fail_notFound() throws Exception {
            EmailVerifyRequest request = new EmailVerifyRequest("unknown@example.com");
            doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                    .when(userService).sendVerificationEmail(any(EmailVerifyRequest.class));

            mockMvc.perform(post("/api/auth/email/verify/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("이메일 인증 발송 실패 - 재발송 제한 429")
        @WithMockUser
        void send_fail_rateLimit() throws Exception {
            EmailVerifyRequest request = new EmailVerifyRequest("test@example.com");
            doThrow(new BusinessException(UserErrorCode.TOO_MANY_REQUESTS))
                    .when(userService).sendVerificationEmail(any(EmailVerifyRequest.class));

            mockMvc.perform(post("/api/auth/email/verify/send")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        }
    }

    @Nested
    @DisplayName("이메일 인증 확인 API")
    class EmailVerifyConfirm {

        @Test
        @DisplayName("이메일 인증 확인 성공 - 200 OK")
        @WithMockUser
        void confirm_success() throws Exception {
            EmailVerifyConfirmRequest request = new EmailVerifyConfirmRequest("valid-token");
            doNothing().when(userService).confirmEmailVerification(any(EmailVerifyConfirmRequest.class));

            mockMvc.perform(post("/api/auth/email/verify/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."));
        }

        @Test
        @DisplayName("이메일 인증 확인 실패 - 만료된 토큰 401")
        @WithMockUser
        void confirm_fail_expired() throws Exception {
            EmailVerifyConfirmRequest request = new EmailVerifyConfirmRequest("expired-token");
            doThrow(new BusinessException(UserErrorCode.TOKEN_EXPIRED))
                    .when(userService).confirmEmailVerification(any(EmailVerifyConfirmRequest.class));

            mockMvc.perform(post("/api/auth/email/verify/confirm")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }
    }
}
