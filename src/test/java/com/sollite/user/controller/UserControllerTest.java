package com.sollite.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.exception.GlobalExceptionHandler;
import com.sollite.global.security.JwtTokenProvider;
import com.sollite.user.dto.PasswordChangeRequest;
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

import com.sollite.user.dto.ProfileUpdateRequest;
import com.sollite.user.dto.UserProfileResponse;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UserProfileResponse createProfileResponse() {
        return new UserProfileResponse(1L, "test@example.com", "홍길동", "010-1234-5678", true, LocalDateTime.of(2026, 3, 16, 0, 0));
    }

    @Nested
    @DisplayName("내 정보 조회 API")
    class GetProfile {

        @Test
        @DisplayName("내 정보 조회 성공 - 200 OK")
        @WithMockUser(username = "1")
        void getProfile_success() throws Exception {
            given(userService.getProfile(1L)).willReturn(createProfileResponse());

            mockMvc.perform(get("/api/users/me")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.emailVerified").value(true));
        }
    }

    @Nested
    @DisplayName("내 정보 수정 API")
    class UpdateProfile {

        @Test
        @DisplayName("내 정보 수정 성공 - 200 OK")
        @WithMockUser(username = "1")
        void updateProfile_success() throws Exception {
            ProfileUpdateRequest request = new ProfileUpdateRequest("김길동", "010-9876-5432");
            UserProfileResponse response = new UserProfileResponse(1L, "test@example.com", "김길동", "010-9876-5432", true, LocalDateTime.of(2026, 3, 16, 0, 0));
            given(userService.updateProfile(eq(1L), any(ProfileUpdateRequest.class))).willReturn(response);

            mockMvc.perform(patch("/api/users/me")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("정보가 수정되었습니다."))
                    .andExpect(jsonPath("$.user.name").value("김길동"))
                    .andExpect(jsonPath("$.user.phone").value("010-9876-5432"));
        }

        @Test
        @DisplayName("내 정보 수정 실패 - 연락처 형식 오류 400")
        @WithMockUser(username = "1")
        void updateProfile_fail_invalidPhone() throws Exception {
            ProfileUpdateRequest request = new ProfileUpdateRequest("김길동", "invalid-phone");

            mockMvc.perform(patch("/api/users/me")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 API")
    class ChangePassword {

        @Test
        @DisplayName("비밀번호 변경 성공 - 200 OK")
        @WithMockUser(username = "1")
        void changePassword_success() throws Exception {
            PasswordChangeRequest request = new PasswordChangeRequest("Test1234!", "NewPass1!", "NewPass1!");
            doNothing().when(userService).changePassword(eq(1L), any(PasswordChangeRequest.class));

            mockMvc.perform(patch("/api/users/me/password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."));
        }

        @Test
        @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치 401")
        @WithMockUser(username = "1")
        void changePassword_fail_wrongCurrent() throws Exception {
            PasswordChangeRequest request = new PasswordChangeRequest("Wrong1234!", "NewPass1!", "NewPass1!");
            doThrow(new BusinessException(UserErrorCode.INVALID_PASSWORD))
                    .when(userService).changePassword(eq(1L), any(PasswordChangeRequest.class));

            mockMvc.perform(patch("/api/users/me/password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        }

        @Test
        @DisplayName("비밀번호 변경 실패 - 유효성 검증 400")
        @WithMockUser(username = "1")
        void changePassword_fail_validation() throws Exception {
            PasswordChangeRequest request = new PasswordChangeRequest("", "short", "short");

            mockMvc.perform(patch("/api/users/me/password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }
}
