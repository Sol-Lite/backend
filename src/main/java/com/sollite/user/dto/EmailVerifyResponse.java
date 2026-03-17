package com.sollite.user.dto;

public record EmailVerifyResponse(
        String message,
        long expiresIn
) {}
