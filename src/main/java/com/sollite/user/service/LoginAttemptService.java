package com.sollite.user.service;

import com.sollite.user.domain.entity.User;
import com.sollite.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시도 이력을 관리하는 서비스.
 * 로그인 실패 횟수를 기록하고, 5회 연속 실패 시 계정을 자동으로 잠급니다.
 * 로그인 성공 시 실패 횟수를 초기화합니다.
 * 별도의 @Transactional 메서드로 분리하여 트랜잭션 롤백 문제를 방지합니다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    /**
     * 로그인 실패를 기록합니다.
     * 사용자의 실패 횟수를 증가시키고, 5회 실패 시 계정을 잠급니다.
     *
     * @param user 로그인 시도한 사용자
     */
    @Transactional
    public void recordFailure(User user) {
        user.incrementLoginFailCount();
        userRepository.save(user);
    }

    /**
     * 로그인 성공을 기록합니다.
     * 사용자의 로그인 실패 횟수를 초기화합니다.
     *
     * @param user 로그인에 성공한 사용자
     */
    @Transactional
    public void recordSuccess(User user) {
        user.resetLoginFailCount();
        userRepository.save(user);
    }

    @Transactional
    public void unlockIfExpired(User user) {
        if (user.isLockExpired()) {
            user.unlock();
            userRepository.save(user);
        }
    }
}
