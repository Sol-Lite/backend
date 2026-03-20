package com.sollite.foreignmarket.dto;

public record LsG3104Req(
        G3104InBlock g3104InBlock
) {
    public record G3104InBlock(
            String delaygb,
            String keysymbol,
            String exchcd,
            String symbol
    ) {}
}
