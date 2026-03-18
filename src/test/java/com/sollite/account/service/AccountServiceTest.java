package com.sollite.account.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.account.domain.repository.AccountRepository;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import com.sollite.account.dto.AccountInfoResponse;
import com.sollite.account.exception.AccountErrorCode;
import com.sollite.global.exception.BusinessException;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.exception.UserErrorCode;
import com.sollite.user.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @InjectMocks
    private AccountService accountService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SimulationRoundRepository simulationRoundRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private User createUser() {
        return User.builder()
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .name("홍길동")
                .phone("01012345678")
                .build();
    }

    private Account createAccount(User user) {
        return Account.builder()
                .user(user)
                .accountNo("270-86-123456")
                .accountName("종합계좌 홍길동")
                .accountPinHash("encodedPin")
                .investmentTendency(InvestmentType.BALANCED)
                .build();
    }

    @Nested
    @DisplayName("계좌 생성")
    class CreateAccount {

        @Test
        @DisplayName("계좌 생성 성공")
        void createAccount_success() {
            User user = createUser();
            given(accountRepository.existsByAccountNo(anyString())).willReturn(false);
            given(passwordEncoder.encode("1234")).willReturn("encodedPin");
            given(accountRepository.save(any(Account.class))).willAnswer(i -> i.getArgument(0));
            given(simulationRoundRepository.save(any())).willAnswer(i -> i.getArgument(0));

            Account account = accountService.createAccount(user, InvestmentType.BALANCED, "1234");

            assertThat(account.getAccountNo()).startsWith("270-86-");
            assertThat(account.getAccountName()).isEqualTo("종합계좌 홍길동");
            assertThat(account.getInvestmentTendency()).isEqualTo(InvestmentType.BALANCED);
            verify(accountRepository).save(any(Account.class));
            verify(simulationRoundRepository).save(any());
        }
    }

    @Nested
    @DisplayName("계좌 정보 조회")
    class GetMyAccount {

        @Test
        @DisplayName("계좌 조회 성공")
        void getMyAccount_success() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));

            AccountInfoResponse response = accountService.getMyAccount(1L);

            assertThat(response.accountNumber()).isEqualTo("270-86-123456");
            assertThat(response.accountName()).isEqualTo("종합계좌 홍길동");
            assertThat(response.investmentType()).isEqualTo(InvestmentType.BALANCED);
        }

        @Test
        @DisplayName("계좌 조회 실패 - 계좌 없음")
        void getMyAccount_fail_notFound() {
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getMyAccount(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("계좌 PIN 인증")
    class VerifyPin {

        @Test
        @DisplayName("PIN 인증 성공")
        void verifyPin_success() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("1234", "encodedPin")).willReturn(true);

            assertThatNoException().isThrownBy(() -> accountService.verifyPin(1L, "1234"));
        }

        @Test
        @DisplayName("PIN 인증 실패 - 불일치")
        void verifyPin_fail_wrongPin() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("9999", "encodedPin")).willReturn(false);

            assertThatThrownBy(() -> accountService.verifyPin(1L, "9999"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_PIN));
        }

        @Test
        @DisplayName("PIN 인증 실패 - 계좌 없음")
        void verifyPin_fail_noAccount() {
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.verifyPin(1L, "1234"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("계좌 PIN 변경")
    class ChangePin {

        @Test
        @DisplayName("PIN 변경 성공")
        void changePin_success() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("1234", "encodedPin")).willReturn(true);
            given(passwordEncoder.encode("5678")).willReturn("newEncodedPin");

            accountService.changePin(1L, "1234", "5678");

            assertThat(account.getAccountPinHash()).isEqualTo("newEncodedPin");
        }

        @Test
        @DisplayName("PIN 변경 실패 - 현재 PIN 불일치")
        void changePin_fail_wrongCurrentPin() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("9999", "encodedPin")).willReturn(false);

            assertThatThrownBy(() -> accountService.changePin(1L, "9999", "5678"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_PIN));
        }

        @Test
        @DisplayName("PIN 변경 실패 - 계좌 없음")
        void changePin_fail_noAccount() {
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.changePin(1L, "1234", "5678"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("계좌 PIN 재설정")
    class PinReset {

        @Test
        @DisplayName("PIN 재설정 메일 발송 성공")
        void requestPinReset_success() {
            User user = createUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            accountService.requestPinReset(1L);

            verify(emailService).sendPinResetEmail("test@example.com", 1L);
        }

        @Test
        @DisplayName("PIN 재설정 메일 발송 실패 - 사용자 없음")
        void requestPinReset_fail_userNotFound() {
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.requestPinReset(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("PIN 재설정 확인 성공")
        void confirmPinReset_success() {
            User user = createUser();
            Account account = createAccount(user);
            given(emailService.verifyPinResetToken("valid-token"))
                    .willReturn(Map.of("user_id", "1", "email", "test@example.com"));
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.encode("5678")).willReturn("newEncodedPin");

            accountService.confirmPinReset("valid-token", "5678");

            assertThat(account.getAccountPinHash()).isEqualTo("newEncodedPin");
        }

        @Test
        @DisplayName("PIN 재설정 확인 실패 - 만료된 토큰")
        void confirmPinReset_fail_expiredToken() {
            given(emailService.verifyPinResetToken("expired-token"))
                    .willThrow(new BusinessException(UserErrorCode.TOKEN_EXPIRED));

            assertThatThrownBy(() -> accountService.confirmPinReset("expired-token", "5678"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(UserErrorCode.TOKEN_EXPIRED));
        }
    }

    @Nested
    @DisplayName("계좌 폐쇄")
    class CloseAccount {

        @Test
        @DisplayName("계좌 폐쇄 성공")
        void closeAccount_success() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("1234", "encodedPin")).willReturn(true);

            accountService.closeAccount(1L, "1234");

            assertThat(account.isActive()).isFalse();
        }

        @Test
        @DisplayName("계좌 폐쇄 실패 - PIN 불일치")
        void closeAccount_fail_wrongPin() {
            User user = createUser();
            Account account = createAccount(user);
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));
            given(passwordEncoder.matches("9999", "encodedPin")).willReturn(false);

            assertThatThrownBy(() -> accountService.closeAccount(1L, "9999"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_PIN));
        }

        @Test
        @DisplayName("계좌 폐쇄 실패 - 이미 폐쇄된 계좌")
        void closeAccount_fail_alreadyClosed() {
            User user = createUser();
            Account account = createAccount(user);
            account.close();
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.closeAccount(1L, "1234"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE));
        }

        @Test
        @DisplayName("계좌 폐쇄 실패 - 계좌 없음")
        void closeAccount_fail_noAccount() {
            given(accountRepository.findByUser_UserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.closeAccount(1L, "1234"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND));
        }
    }
}
