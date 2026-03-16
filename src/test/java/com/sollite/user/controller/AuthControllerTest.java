package com.sollite.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.exception.GlobalExceptionHandler;
import com.sollite.user.dto.SignupRequest;
import com.sollite.user.dto.SignupResponse;
import com.sollite.user.exception.UserErrorCode;
import com.sollite.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("회원가입 API 성공 - 201 Created")
    @WithMockUser
    void signup_success() throws Exception {
        // given
        SignupRequest request = new SignupRequest(
                "test@example.com", "Test1234!", "Test1234!",
                "홍길동", "010-1234-5678", true, true, false
        );
        SignupResponse response = new SignupResponse(
                1L, "test@example.com", "홍길동",
                "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요."
        );
        given(userService.signup(any(SignupRequest.class))).willReturn(response);

        // when & then
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
        // given
        SignupRequest request = new SignupRequest(
                "test@example.com", "Test1234!", "Test1234!",
                "홍길동", null, true, true, false
        );
        given(userService.signup(any(SignupRequest.class)))
                .willThrow(new BusinessException(UserErrorCode.DUPLICATE_EMAIL));

        // when & then
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
        // given - 이메일 빈값, 비밀번호 규칙 미충족
        SignupRequest request = new SignupRequest(
                "", "short", "short",
                "", null, false, false, false
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors").exists());
    }
}
