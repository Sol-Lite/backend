package com.sollite.notifications.service;

import com.sollite.notifications.domain.entity.NotificationSetting;
import com.sollite.notifications.domain.enums.NotificationType;
import com.sollite.notifications.domain.repository.NotificationSettingRepository;
import com.sollite.notifications.domain.repository.PriceAlertRepository;
import com.sollite.notifications.dto.NotificationSettingRequest;
import com.sollite.notifications.dto.NotificationSettingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final PriceAlertRepository priceAlertRepository;

    public NotificationSettingResponse getSettings(Long userId) {
        return settingRepository.findByUserId(userId)
                .map(NotificationSettingResponse::from)
                .orElse(NotificationSettingResponse.defaultSettings());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "notificationSettings", key = "#userId + ':EXECUTION'"),
        @CacheEvict(value = "notificationSettings", key = "#userId + ':PRICE_ALERT'")
    })
    public NotificationSettingResponse updateSettings(Long userId, NotificationSettingRequest request) {
        NotificationSetting setting = getOrCreateSetting(userId);

        // DB 컬럼 NOT NULL 미지정으로 null 가능, BigDecimal.compareTo()로 scale 무관 비교
        BigDecimal currentThreshold = setting.getDefaultThresholdPercent();
        boolean thresholdChanged = currentThreshold == null
                || request.defaultThresholdPercent().compareTo(currentThreshold) != 0;

        setting.update(
                request.priceAlertEnabled(),
                request.executionAlertEnabled(),
                request.defaultThresholdPercent()
        );

        if (thresholdChanged) {
            priceAlertRepository.updateThresholdByUserId(userId, request.defaultThresholdPercent());
        }

        return NotificationSettingResponse.from(setting);
    }

    // 틱마다 호출되므로 @Cacheable로 DB 조회 최소화
    @Cacheable(value = "notificationSettings", key = "#userId + ':' + #type.name()")
    public boolean isEnabled(Long userId, NotificationType type) {
        return settingRepository.findByUserId(userId)
                .map(s -> switch (type) {
                    case EXECUTION -> s.isExecutionAlertEnabled();
                    case PRICE_ALERT -> s.isPriceAlertEnabled();
                })
                .orElse(true);
    }

    // private 메서드라 Spring AOP 프록시 미적용 — updateSettings()의 @Transactional 컨텍스트 안에서 실행
    private NotificationSetting getOrCreateSetting(Long userId) {
        return settingRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    try {
                        return settingRepository.save(new NotificationSetting(userId));
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청으로 UNIQUE 충돌 시 재조회
                        return settingRepository.findByUserIdWithLock(userId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "알림 설정 초기화 실패 - userId=" + userId));
                    }
                });
    }
}
