package com.sollite.foreignmarket.exception;

import com.sollite.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ForeignStockErrorCode implements ErrorCode {
    FOREIGN_STOCK_API_ERROR("FOREIGN_STOCK_001", "해외주식 API 호출 실패", HttpStatus.INTERNAL_SERVER_ERROR),
    FOREIGN_STOCK_DATA_NOT_FOUND("FOREIGN_STOCK_002", "해외주식 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_EXCHANGE_CODE("FOREIGN_STOCK_003", "유효하지 않은 거래소 코드입니다", HttpStatus.BAD_REQUEST),
    INVALID_STOCK_CODE("FOREIGN_STOCK_004", "유효하지 않은 종목 코드입니다", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ForeignStockErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public int getStatus() {
        return httpStatus.value();
    }
}
