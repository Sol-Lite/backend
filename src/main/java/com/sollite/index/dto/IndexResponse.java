package com.sollite.index.dto;

public record IndexResponse(
        String code,
        String name,
        double price,
        String sign,
        double change,
        double changeRate
) {}
