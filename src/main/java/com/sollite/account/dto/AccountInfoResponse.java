package com.sollite.account.dto;

import com.sollite.account.domain.enums.InvestmentType;

import java.time.LocalDateTime;

public record AccountInfoResponse(
        Long accountId,
        String accountNumber,
        String accountName,
        InvestmentType investmentType,
        String accountStatus,
        LocalDateTime createdAt
) {
}
