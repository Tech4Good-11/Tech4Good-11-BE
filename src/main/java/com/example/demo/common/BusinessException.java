package com.example.demo.common;

import lombok.Getter;

/**
 * 서비스 로직에서 던지는 예외. throw new BusinessException(ErrorCode.NOT_FOUND) 형태로 사용.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
