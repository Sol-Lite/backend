package com.sollite.balance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.balance.snapshot", name = "bootstrap-on-startup", havingValue = "true")
public class PortfolioSnapshotBootstrapRunner implements ApplicationRunner {

    private final BalanceService balanceService;

    @Override
    public void run(ApplicationArguments args) {
        balanceService.captureSnapshotsForActiveRounds("BOOTSTRAP");
    }
}
