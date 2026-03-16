package com.sollite.global.exception;

public interface ErrorCode {
    int getStatus(); // HTTP 상태 코드를 반환
    String name(); // Enum 상수의 이름을 문자열로 반환
    String getMessage(); // 사용자에게 보여줄 메시지
}
