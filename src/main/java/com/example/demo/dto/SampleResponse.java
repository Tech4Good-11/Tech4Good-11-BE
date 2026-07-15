package com.example.demo.dto;

import com.example.demo.domain.Sample;

/**
 * 응답 DTO. 엔티티를 그대로 노출하지 않고 이 DTO로 변환해서 반환.
 */
public record SampleResponse(
        Long id,
        String name,
        String description
) {
    public static SampleResponse from(Sample sample) {
        return new SampleResponse(sample.getId(), sample.getName(), sample.getDescription());
    }
}
