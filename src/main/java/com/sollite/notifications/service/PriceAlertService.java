package com.sollite.notifications.service;

import com.sollite.notifications.domain.repository.PriceAlertRepository;
import com.sollite.notifications.dto.PriceAlertResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가격 알림 조회 서비스.
 * 알림 생성/삭제는 WatchlistService에서 관심종목 추가/삭제 시 자동 처리된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceAlertService {

    private final PriceAlertRepository priceAlertRepository;

    public List<PriceAlertResponse> getMyAlerts(Long userId) {
        return priceAlertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PriceAlertResponse::from)
                .toList();
    }
}
