package com.sollite.account.dto;

import java.math.BigDecimal;

public record AccountOpenResponse(
        Long accountId,
        String accountNumber,
        BigDecimal seedMoney,
        String message
) {
}
