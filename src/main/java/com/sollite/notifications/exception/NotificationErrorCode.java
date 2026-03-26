package com.sollite.notifications.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다"),
    PRICE_ALERT_NOT_FOUND(404, "가격 알림을 찾을 수 없습니다"),
    PRICE_ALERT_ALREADY_EXISTS(409, "해당 종목에 이미 활성화된 가격 알림이 있습니다"),
    INSTRUMENT_NOT_FOUND(404, "존재하지 않는 종목입니다"),
    INVALID_ALERT_REQUEST(400, "알림 설정 값이 유효하지 않습니다");

    private final int status;
    private final String message;
}
