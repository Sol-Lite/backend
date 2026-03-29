package com.sollite.balance.service;

import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.account.domain.enums.RoundStatus;
import com.sollite.account.domain.repository.SimulationRoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.balance.snapshot", name = "bootstrap-on-startup", havingValue = "true")
public class PortfolioSnapshotBootstrapRunner implements ApplicationRunner {

    private final SimulationRoundRepository simulationRoundRepository;
    private final BalanceService balanceService;

    @Override
    public void run(ApplicationArguments args) {
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
                log.error("[SNAPSHOT] 부트스트랩 스냅샷 적재 실패 - accountId={}, roundId={}, error={}",
                        round.getAccount().getAccountId(),
                        round.getSimulationRoundId(),
                        e.getMessage(), e);
            }
        }

        log.info("[SNAPSHOT] 부트스트랩 스냅샷 적재 완료 - total={}, success={}, failed={}",
                activeRounds.size(), success, failed);
    }
}
