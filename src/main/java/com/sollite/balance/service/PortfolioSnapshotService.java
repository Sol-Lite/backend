package com.sollite.balance.service;

import com.sollite.account.domain.entity.Account;
import com.sollite.account.domain.entity.SimulationRound;
import com.sollite.balance.domain.entity.PortfolioSnapshot;
import com.sollite.balance.domain.repository.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 포트폴리오 스냅샷 저장 전담 서비스
 * 별도 트랜잭션(REQUIRES_NEW)에서 실행되어 외부 IO 완료 후 DB만 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    /**
     * 포트폴리오 스냅샷 저장 (새 트랜잭션)
     * calculateValuation이 이미 외부 IO를 완료했으므로, 여기서는 DB 저장만 수행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertTodaySnapshot(
            Account account,
            SimulationRound round,
            BigDecimal totalAssets,
            BigDecimal cashKrwAmount,
            BigDecimal cashUsdAmount,
            BigDecimal usdKrwRate,
            BigDecimal totalStockEvaluation) {

        LocalDate today = LocalDate.now();
        BigDecimal dailyReturn = computeDailyReturnRate(
                account.getAccountId(),
                round.getSimulationRoundId(),
                today,
                totalAssets);

        PortfolioSnapshot snapshot = portfolioSnapshotRepository
                .findByAccount_AccountIdAndSimulationRound_SimulationRoundIdAndSnapshotDate(
                        account.getAccountId(), round.getSimulationRoundId(), today)
                .orElseGet(() -> PortfolioSnapshot.builder()
                        .account(account)
                        .simulationRound(round)
                        .snapshotDate(today)
                        .totalValue(BigDecimal.ZERO)
                        .cashKrw(BigDecimal.ZERO)
                        .cashUsd(BigDecimal.ZERO)
                        .usdExchangeRate(BigDecimal.ONE)
                        .stockValue(BigDecimal.ZERO)
                        .dailyReturn(BigDecimal.ZERO)
                        .build());

        snapshot.updateMetrics(
                totalAssets,
                cashKrwAmount,
                cashUsdAmount,
                usdKrwRate.compareTo(BigDecimal.ZERO) > 0 ? usdKrwRate : BigDecimal.ONE,
                totalStockEvaluation,
                dailyReturn
        );
        portfolioSnapshotRepository.save(snapshot);
    }

    /**
     * 일일 수익률 계산
     */
    private BigDecimal computeDailyReturnRate(Long accountId, Long roundId, LocalDate snapshotDate, BigDecimal totalAssets) {
        return portfolioSnapshotRepository.findPreviousSnapshots(
                accountId, roundId, snapshotDate, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(previous -> previous.getTotalValue().compareTo(BigDecimal.ZERO) > 0
                        ? totalAssets.divide(previous.getTotalValue(), 6, RoundingMode.HALF_UP)
                                .subtract(BigDecimal.ONE)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }
}
