package com.sollite.balance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotScheduler {

    private final BalanceService balanceService;

    @Value("${app.balance.snapshot.enabled:true}")
    private boolean enabled;

    /**
     * 국내+미국 혼합 포트폴리오 기준으로 미국장 종료 이후(KST 오전) 일일 스냅샷 적재.
     */
    @Scheduled(cron = "${app.balance.snapshot.cron:0 0 7 * * MON-FRI}", zone = "Asia/Seoul")
    public void captureDailySnapshots() {
        if (!enabled) {
            return;
        }
        balanceService.captureSnapshotsForActiveRounds("SCHEDULER");
    }
}
