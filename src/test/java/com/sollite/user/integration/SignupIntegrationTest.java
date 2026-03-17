package com.sollite.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.SignupRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.findByEmail("integration@test.com")
                .ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("통합 테스트 - 회원가입 → DB 저장 검증")
    void signup_integration() throws Exception {
        // given
        SignupRequest request = new SignupRequest(
                "integration@test.com", "Test1234!", "Test1234!",
                "통합테스트", "010-9999-9999", true, true, false
        );

        // when - API 호출
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("integration@test.com"))
                .andExpect(jsonPath("$.name").value("통합테스트"));

        // then - DB 검증
        Optional<User> savedUser = userRepository.findByEmail("integration@test.com");
        assertThat(savedUser).isPresent();

        User user = savedUser.get();
        assertThat(user.getName()).isEqualTo("통합테스트");
        assertThat(user.getPhone()).isEqualTo("010-9999-9999");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("Test1234!"); // BCrypt 인코딩 확인
    }

    @Test
    @DisplayName("통합 테스트 - 이메일 중복 가입 시 409")
    void signup_duplicate_integration() throws Exception {
        // given - 첫 번째 가입
        SignupRequest request = new SignupRequest(
                "integration@test.com", "Test1234!", "Test1234!",
                "첫번째", null, true, true, false
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // when - 같은 이메일로 두 번째 가입
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }
}
