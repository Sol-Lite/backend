package com.sollite.foreignmarket.dto;

public record LsG3106Req(
        G3106InBlock g3106InBlock
) {
    public record G3106InBlock(
            String delaygb,      // 지연구분
            String keysymbol,    // KEY종목코드: 82TSLA
            String exchcd,       // 거래소코드: 81=뉴욕/아멕스, 82=나스닥
            String symbol        // 종목코드: TSLA
    ) {}
}
