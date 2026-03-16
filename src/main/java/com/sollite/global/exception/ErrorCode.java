package com.sollite.global.exception;

public interface ErrorCode {
    int getStatus();
    String name();
    String getMessage();
}
