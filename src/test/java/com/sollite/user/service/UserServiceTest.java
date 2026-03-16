package com.sollite.user.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.SignupRequest;
import com.sollite.user.dto.SignupResponse;
import com.sollite.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SignupRequest createSignupRequest() {
        return new SignupRequest(
                "test@example.com",
                "Test1234!",
                "Test1234!",
                "홍길동",
                "010-1234-5678",
                true,
                true,
                false
        );
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        // given
        SignupRequest request = createSignupRequest();
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignupResponse response = userService.signup(request);

        // then
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.message()).contains("회원가입이 완료되었습니다");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_fail_duplicateEmail() {
        // given
        SignupRequest request = createSignupRequest();
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
                });
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 확인 불일치")
    void signup_fail_passwordMismatch() {
        // given
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "Test1234!",
                "Different1!",
                "홍길동",
                null,
                true,
                true,
                false
        );

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
                });
    }
}
