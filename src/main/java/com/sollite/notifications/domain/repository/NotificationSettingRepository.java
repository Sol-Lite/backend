package com.sollite.notifications.domain.repository;

import com.sollite.notifications.domain.entity.NotificationSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM NotificationSetting s WHERE s.userId = :userId")
    Optional<NotificationSetting> findByUserIdWithLock(@Param("userId") Long userId);
}
