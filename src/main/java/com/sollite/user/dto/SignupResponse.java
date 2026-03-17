package com.sollite.user.dto;

public record SignupResponse(
        Long userId,
        String email,
        String name,
        String message
) {
}
