package com.example.demo.dto.elder;

import com.example.demo.domain.Elder;
import com.example.demo.domain.enums.Gender;
import com.example.demo.domain.enums.Relationship;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 시니어 목록 요약(대시보드 진입용). */
public record ElderSummaryResponse(
        Long id,
        String name,
        LocalDate birthDate,
        Gender gender,
        Relationship relationship,
        long activeMedicationCount,
        long activeDiseaseCount,
        LocalDateTime lastCheckinAt
) {
    public static ElderSummaryResponse of(Elder elder, Relationship relationship,
                                          long activeMedicationCount, long activeDiseaseCount,
                                          LocalDateTime lastCheckinAt) {
        return new ElderSummaryResponse(
                elder.getId(),
                elder.getName(),
                elder.getBirthDate(),
                elder.getGender(),
                relationship,
                activeMedicationCount,
                activeDiseaseCount,
                lastCheckinAt
        );
    }
}
