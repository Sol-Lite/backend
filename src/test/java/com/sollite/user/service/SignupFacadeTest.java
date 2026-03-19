package com.sollite.user.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.enums.InvestmentType;
import com.sollite.account.service.AccountService;
import com.sollite.user.domain.entity.User;
import com.sollite.user.dto.SignupRequest;
import com.sollite.user.dto.SignupResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignupFacadeTest {

    @InjectMocks
    private SignupFacade signupFacade;

    @Mock
    private UserService userService;

    @Mock
    private AccountService accountService;

    @Test
    @DisplayName("회원가입+계좌개설 성공 - UserService와 AccountService 순서대로 호출")
    void signup_success() {
        SignupRequest request = new SignupRequest(
                "test@example.com", "Test1234!", "Test1234!",
                "홍길동", "010-1234-5678", true, true,
                InvestmentType.BALANCED, "1234"
        );

        User user = User.builder()
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .name("홍길동")
                .phone("01012345678")
                .build();

        Account account = Account.builder()
                .user(user)
                .accountNo("270-86-123456")
                .accountName("종합계좌 홍길동")
                .accountPinHash("encodedPin")
                .investmentTendency(InvestmentType.BALANCED)
                .build();

        given(userService.createUser(request)).willReturn(user);
        given(accountService.createAccount(user, InvestmentType.BALANCED, "1234")).willReturn(account);

        SignupResponse response = signupFacade.signup(request);

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.accountNo()).isEqualTo("270-86-123456");
        assertThat(response.message()).contains("회원가입이 완료되었습니다");

        verify(userService).createUser(request);
        verify(accountService).createAccount(user, InvestmentType.BALANCED, "1234");
    }
}
