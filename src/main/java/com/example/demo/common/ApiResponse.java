package com.example.demo.common;

import lombok.Getter;

/**
 * 모든 API의 공통 응답 포맷.
 * 성공: ApiResponse.success(data) / ApiResponse.success("메시지", data)
 * 실패: 예외를 던지면 GlobalExceptionHandler가 error 포맷으로 변환.
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
