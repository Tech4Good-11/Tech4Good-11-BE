package com.example.demo.dto.dailylog;

import java.time.LocalDate;

/**
 * "오늘치 복용한 약" 항목.
 * taken=null 이면 아직 확인되지 않음(대화/체크 전) → 프론트에서 '미확인' 표시.
 */
public record MedicationIntakeResponse(
        Long medicationId,
        String medicationName,
        String dosage,
        Boolean taken,
        LocalDate intakeDate
) {
}
