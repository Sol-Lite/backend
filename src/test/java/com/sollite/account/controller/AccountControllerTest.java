package com.sollite.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.account.dto.PinChangeRequest;
import com.sollite.account.dto.PinVerifyRequest;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.account.service.AccountService;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.exception.GlobalExceptionHandler;
import com.sollite.global.security.JwtTokenProvider;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("계좌 PIN 변경 API")
    class ChangePin {

        @Test
        @DisplayName("현재 PIN 형식 오류 시 400")
        @WithMockUser(username = "1")
        void changePin_fail_invalidCurrentPinFormat() throws Exception {
            PinChangeRequest request = new PinChangeRequest("12ab", "5678");

            mockMvc.perform(patch("/api/accounts/me/pin")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors.currentPin").value("계좌 비밀번호는 숫자 4자리입니다"));

            verify(accountService, never()).changePin(eq(1L), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("계좌 PIN 인증 API")
    class VerifyPin {

        @Test
        @DisplayName("PIN 불일치 시 403")
        @WithMockUser(username = "1")
        void verifyPin_fail_wrongPin() throws Exception {
            PinVerifyRequest request = new PinVerifyRequest("9999");
            doThrow(new BusinessException(AccountErrorCode.INVALID_PIN))
                    .when(accountService).verifyPin(1L, "9999");

            mockMvc.perform(post("/api/accounts/verify-pin")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INVALID_PIN"))
                    .andExpect(jsonPath("$.message").value("계좌 비밀번호가 올바르지 않습니다"));
        }
    }
}
