package com.sollite.balance.service;

import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotScheduler {

    private final SimulationRoundRepository simulationRoundRepository;
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

        List<SimulationRound> activeRounds =
                simulationRoundRepository.findAllActiveWithAccountByRoundStatus(RoundStatus.ACTIVE);

        int success = 0;
        int failed = 0;

        for (SimulationRound round : activeRounds) {
            try {
                balanceService.captureDailySnapshot(round.getAccount(), round);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("[SNAPSHOT] 일일 스냅샷 적재 실패 - accountId={}, roundId={}, error={}",
                        round.getAccount().getAccountId(),
                        round.getSimulationRoundId(),
                        e.getMessage(), e);
            }
        }

        log.info("[SNAPSHOT] 일일 스냅샷 적재 완료 - total={}, success={}, failed={}",
                activeRounds.size(), success, failed);
    }
}
