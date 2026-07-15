package com.example.demo.dto.medication;

import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.enums.MedicationStatus;

import java.time.LocalDate;

public record MedicationResponse(
        Long id,
        String medicationName,
        String atcCode,
        String dosage,
        Integer intervalHours,
        LocalDate startDate,
        LocalDate endDate,
        MedicationStatus status
) {
    public static MedicationResponse from(ElderMedication m) {
        return new MedicationResponse(
                m.getId(),
                m.getMedicationName(),
                m.getAtcCode(),
                m.getDosage(),
                m.getIntervalHours(),
                m.getStartDate(),
                m.getEndDate(),
                m.getStatus()
        );
    }
}
