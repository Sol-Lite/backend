package com.sollite.user.domain.repository;

import com.sollite.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 정보에 대한 데이터 접근을 담당하는 리포지토리.
 * JPA를 통해 User 엔티티를 데이터베이스와 동기화합니다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자를 조회합니다.
     *
     * @param email 조회할 이메일 주소
     * @return 해당 이메일의 사용자 정보 (없으면 빈 Optional)
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일의 존재 여부를 확인합니다.
     *
     * @param email 확인할 이메일 주소
     * @return 등록된 이메일이면 true, 아니면 false
     */
    boolean existsByEmail(String email);
}
