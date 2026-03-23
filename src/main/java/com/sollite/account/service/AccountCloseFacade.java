package com.sollite.account.service;

import com.sollite.balance.service.BalanceService;
import com.sollite.order.service.OrderService;
import com.sollite.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌 폐쇄를 단일 트랜잭션으로 처리하는 Facade.
 * AccountService(계좌/PIN 검증), BalanceService(잔고/보유종목 검증),
 * OrderService(미체결 주문 검증)를 조율합니다.
 */
@Service
@RequiredArgsConstructor
public class AccountCloseFacade {

    private final AccountService accountService;
    private final BalanceService balanceService;
    private final OrderService orderService;
    private final UserService userService;

    @Transactional
    public void closeAccount(Long userId, String accountPin) {
        AccountService.CloseContext ctx = accountService.validateAndGetCloseContext(userId, accountPin);
        Long accountId = ctx.account().getAccountId();
        Long roundId = ctx.round().getSimulationRoundId();

        balanceService.validateCashForClose(accountId, roundId);
        balanceService.validateHoldingsForClose(accountId, roundId);
        orderService.validateNoPendingOrders(accountId, roundId);

        accountService.executeClose(ctx);
        userService.withdraw(userId);
    }
}
