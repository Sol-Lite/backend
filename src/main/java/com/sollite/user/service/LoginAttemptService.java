package com.sollite.user.service;

import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    @Transactional
    public void recordFailure(User user) {
        user.incrementLoginFailCount();
        userRepository.save(user);
    }

    @Transactional
    public void recordSuccess(User user) {
        user.resetLoginFailCount();
        userRepository.save(user);
    }
}
