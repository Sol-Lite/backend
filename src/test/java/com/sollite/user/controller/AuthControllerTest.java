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
}
