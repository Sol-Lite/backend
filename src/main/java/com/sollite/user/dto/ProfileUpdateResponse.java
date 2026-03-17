package com.sollite.user.dto;

public record ProfileUpdateResponse(
        String message,
        UserProfileResponse user
) {}
