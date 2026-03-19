package com.sollite.account.domain.repository;

import com.sollite.account.domain.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser_UserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.user.userId = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUser_UserId(Long userId);

    boolean existsByAccountNo(String accountNo);
}
