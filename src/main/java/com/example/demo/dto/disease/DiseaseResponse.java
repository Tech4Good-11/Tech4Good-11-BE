package com.example.demo.dto.disease;

import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.enums.DiseaseStatus;

import java.time.LocalDate;

public record DiseaseResponse(
        Long id,
        String diseaseName,
        String icdCode,
        LocalDate diagnosedAt,
        DiseaseStatus status,
        String notes
) {
    public static DiseaseResponse from(ElderDisease d) {
        return new DiseaseResponse(
                d.getId(),
                d.getDiseaseName(),
                d.getIcdCode(),
                d.getDiagnosedAt(),
                d.getStatus(),
                d.getNotes()
        );
    }
}
