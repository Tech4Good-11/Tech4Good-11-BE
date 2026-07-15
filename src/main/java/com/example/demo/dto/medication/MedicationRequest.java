package com.example.demo.dto.medication;

import com.example.demo.domain.enums.MedicationStatus;

import java.time.LocalDate;

public record MedicationRequest(
        String medicationName,
        String atcCode,
        String dosage,
        Integer intervalHours,
        LocalDate startDate,
        LocalDate endDate,
        MedicationStatus status
) {
}
