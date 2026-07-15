package com.example.demo.dto;

/**
 * 요청 바디 DTO. record라 별도 getter 없이 바로 사용.
 */
public record SampleRequest(
        String name,
        String description
) {
}
