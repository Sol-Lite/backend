package com.sollite.account.domain.repository;

import com.sollite.account.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser_UserId(Long userId);

    boolean existsByUser_UserId(Long userId);

    boolean existsByAccountNo(String accountNo);
}
