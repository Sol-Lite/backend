package com.sollite.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.LoginRequest;
import com.sollite.user.dto.SignupRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_EMAIL = "login-test@test.com";
    private static final String TEST_PASSWORD = "Test1234!";

    @BeforeEach
    void setup() throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD,
                "로그인테스트", "010-0000-0000", true, true, false
        );
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void cleanup() {
        userRepository.findByEmail(TEST_EMAIL)
                .ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("통합 테스트 - 로그인 성공")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.user.name").value("로그인테스트"));
    }

    @Test
    @DisplayName("통합 테스트 - 비밀번호 불일치 시 401")
    void login_fail_wrongPassword() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, "Wrong1234!", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
    }

    @Test
    @DisplayName("통합 테스트 - 5회 실패 후 계정 잠금 423")
    void login_fail_lockAfter5Attempts() throws Exception {
        LoginRequest wrongRequest = new LoginRequest(TEST_EMAIL, "Wrong1234!", false);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(wrongRequest)));
        }

        // 올바른 비밀번호로도 잠금 상태
        LoginRequest correctRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, false);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }
}
