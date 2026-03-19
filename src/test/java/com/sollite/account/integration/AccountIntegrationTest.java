package com.sollite.account.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.AccountStatus;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.account.domain.enums.RoundEndReasonCode;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.dto.AccountCloseRequest;
import com.sollite.account.dto.AccountResetRequest;
import jakarta.persistence.EntityManager;
import com.sollite.user.domain.repository.UserConsentRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SimulationRoundRepository simulationRoundRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_EMAIL = "account-integration@test.com";
    private static final String TEST_PASSWORD = "Test1234!";
    private static final String TEST_ACCOUNT_PIN = "1234";

    @BeforeEach
    void setup() {
        deleteTestData();
        redisTemplate.opsForValue().set("email_verified:" + TEST_EMAIL, "true");
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete("email_verified:" + TEST_EMAIL);
        deleteTestData();
    }

    @Test
    @DisplayName("통합 테스트 - 계좌 리셋 성공 시 이전 라운드 종료 후 새 라운드 생성")
    void resetAccount_success() throws Exception {
        signup();
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(post("/api/accounts/reset")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountResetRequest(TEST_ACCOUNT_PIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("시뮬레이션이 초기화되었습니다."))
                .andExpect(jsonPath("$.roundNo").value(2));

        Account account = getAccount();
        List<SimulationRound> rounds = findRounds(account.getAccountId());

        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(rounds).hasSize(2);

        SimulationRound firstRound = rounds.get(0);
        SimulationRound secondRound = rounds.get(1);

        assertThat(firstRound.getRoundNo()).isEqualTo(1);
        assertThat(firstRound.getRoundStatus()).isEqualTo(RoundStatus.CLOSED);
        assertThat(firstRound.getRoundEndReasonCode()).isEqualTo(RoundEndReasonCode.RESET);
        assertThat(firstRound.getEndedAt()).isNotNull();

        assertThat(secondRound.getRoundNo()).isEqualTo(2);
        assertThat(secondRound.getRoundStatus()).isEqualTo(RoundStatus.ACTIVE);
        assertThat(secondRound.getRoundEndReasonCode()).isNull();
        assertThat(secondRound.getEndedAt()).isNull();
        assertThat(secondRound.getInitialSeedAmount()).isEqualByComparingTo(new BigDecimal("100000000"));
    }

    @Test
    @DisplayName("통합 테스트 - 계좌 리셋 실패 시 라운드 유지")
    void resetAccount_fail_wrongPin() throws Exception {
        signup();
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(post("/api/accounts/reset")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountResetRequest("9999"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        Account account = getAccount();
        List<SimulationRound> rounds = findRounds(account.getAccountId());

        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).getRoundNo()).isEqualTo(1);
        assertThat(rounds.get(0).getRoundStatus()).isEqualTo(RoundStatus.ACTIVE);
        assertThat(rounds.get(0).getRoundEndReasonCode()).isNull();
    }

    @Test
    @DisplayName("통합 테스트 - 계좌 해지 성공 시 계좌와 현재 라운드가 함께 종료")
    void closeAccount_success() throws Exception {
        signup();
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(delete("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountCloseRequest(TEST_ACCOUNT_PIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("계좌가 폐쇄되었습니다."));

        Account account = getAccount();
        List<SimulationRound> rounds = findRounds(account.getAccountId());

        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).getRoundStatus()).isEqualTo(RoundStatus.CLOSED);
        assertThat(rounds.get(0).getRoundEndReasonCode()).isEqualTo(RoundEndReasonCode.ACCOUNT_CLOSED);
        assertThat(rounds.get(0).getEndedAt()).isNotNull();
    }

    private void signup() throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD,
                "계좌통합테스트", "010-0000-1234", true, true,
                InvestmentType.BALANCED, TEST_ACCOUNT_PIN
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetAccessToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, false);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String bearerToken(String accessToken) {
        return "Bearer " + accessToken;
    }

    private Account getAccount() {
        Long userId = userRepository.findByEmail(TEST_EMAIL)
                .orElseThrow()
                .getUserId();

        return accountRepository.findByUser_UserId(userId)
                .orElseThrow();
    }

    private List<SimulationRound> findRounds(Long accountId) {
        entityManager.clear();
        return entityManager.createQuery("""
                        select simulationRound
                        from SimulationRound simulationRound
                        where simulationRound.account.accountId = :accountId
                        order by simulationRound.roundNo asc
                        """, SimulationRound.class)
                .setParameter("accountId", accountId)
                .getResultList();
    }

    private void deleteTestData() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            accountRepository.findByUser_UserId(user.getUserId()).ifPresent(account -> {
                simulationRoundRepository.deleteByAccount_AccountId(account.getAccountId());
                accountRepository.delete(account);
            });
            userConsentRepository.deleteById(user.getUserId());
            userRepository.delete(user);
        });
    }
}
