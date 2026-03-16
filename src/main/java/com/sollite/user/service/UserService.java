package com.sollite.user.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import com.sollite.user.dto.SignupRequest;
import com.sollite.user.dto.SignupResponse;
import com.sollite.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .serviceTermsAgreed(request.serviceTermsAgreed())
                .privacyTermsAgreed(request.privacyTermsAgreed())
                .marketingAgreed(request.marketingAgreed())
                .build();

        userRepository.save(user);

        return new SignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요."
        );
    }
}
