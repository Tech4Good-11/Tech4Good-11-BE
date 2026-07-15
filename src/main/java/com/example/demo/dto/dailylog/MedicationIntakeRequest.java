package com.example.demo.dto.dailylog;

import java.time.LocalDate;

/**
 * 복약 체크 요청(체크리스트 체크박스 연동용).
 * intakeDate 생략 시 오늘.
 */
public record MedicationIntakeRequest(
        Long medicationId,
        Boolean taken,
        LocalDate intakeDate
) {
}
