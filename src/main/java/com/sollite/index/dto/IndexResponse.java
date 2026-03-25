package com.sollite.index.dto;

public record IndexResponse(
        String code,
        String name,
        Double price,
        String sign,
        Double change,
        Double changeRate
) {}
