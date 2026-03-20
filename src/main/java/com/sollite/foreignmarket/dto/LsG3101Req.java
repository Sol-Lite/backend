package com.sollite.foreignmarket.dto;

public record LsG3101Req(
        G3101InBlock g3101InBlock
) {
    public record G3101InBlock(
            String delaygb,      // 지연구분: R
            String keysymbol,    // KEY종목코드: 82TSLA
            String exchcd,       // 거래소코드: 81=뉴욕/아멕스, 82=나스닥
            String symbol        // 종목코드: TSLA
    ) {}
}
