package com.sollite.notifications.domain.repository;

import com.sollite.notifications.domain.entity.PriceAlert;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    /**
     * 가격 변동 체크 시 사용. 비관적 락으로 동시 트리거 방지.
     * 호출 전 existsActiveByInstrumentIds()로 사전 확인 후 호출할 것.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PriceAlert p WHERE p.instrumentId IN :instrumentIds AND p.activeYn = 'Y'")
    List<PriceAlert> findActiveByInstrumentIdsWithLock(@Param("instrumentIds") List<Long> instrumentIds);

    /** 활성 알림 존재 여부 사전 확인 (락 없이 빠르게 조회) */
    @Query("SELECT COUNT(p) > 0 FROM PriceAlert p WHERE p.instrumentId IN :instrumentIds AND p.activeYn = 'Y'")
    boolean existsActiveByInstrumentIds(@Param("instrumentIds") List<Long> instrumentIds);

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndInstrumentIdAndActiveYn(Long userId, Long instrumentId, String activeYn);

    /** 관심종목 삭제 시 연관 알림 제거 */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PriceAlert p WHERE p.userId = :userId AND p.instrumentId = :instrumentId")
    void deleteByUserIdAndInstrumentId(@Param("userId") Long userId, @Param("instrumentId") Long instrumentId);

    /**
     * 서버 기동 시 종목별 활성 알림 수 집계 (ActivePriceAlertRegistry 복구용).
     * 카운터 기반 레지스트리에 실제 알림 수만큼 register()를 호출하기 위해 사용한다.
     * result[0] = instrumentId (Long), result[1] = count (Long)
     */
    @Query("SELECT p.instrumentId, COUNT(p) FROM PriceAlert p WHERE p.activeYn = 'Y' GROUP BY p.instrumentId")
    List<Object[]> countActiveGroupedByInstrumentId();

    /**
     * 당일 미트리거 활성 알림 존재 여부 확인 (락 없이 빠르게 조회).
     * 당일 이미 전부 트리거된 종목은 이 단계에서 조기 종료하여 PESSIMISTIC_WRITE 락 진입을 방지한다.
     */
    @Query("SELECT COUNT(p) > 0 FROM PriceAlert p WHERE p.instrumentId IN :instrumentIds " +
           "AND p.activeYn = 'Y' AND (p.triggeredAt IS NULL OR p.triggeredAt < :todayStart)")
    boolean existsUntriggeredTodayByInstrumentIds(
            @Param("instrumentIds") List<Long> instrumentIds,
            @Param("todayStart") java.time.LocalDateTime todayStart);

    /** 알림 설정 임계값 변경 시 PERCENT 타입 활성 알림만 업데이트 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PriceAlert p SET p.thresholdPercent = :threshold " +
           "WHERE p.userId = :userId AND p.activeYn = 'Y' AND p.alertType = 'PERCENT'")
    int updateThresholdByUserId(@Param("userId") Long userId, @Param("threshold") BigDecimal threshold);
}
