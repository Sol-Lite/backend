package com.sollite.user.dto;

public record SignupResponse(
        Long userId,
        String email,
        String name,
        Long accountId,
        String accountNo,
        String message
) {
}
