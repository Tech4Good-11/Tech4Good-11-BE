package com.example.demo.dto.disease;

import com.example.demo.domain.enums.DiseaseStatus;

import java.time.LocalDate;

public record DiseaseRequest(
        String diseaseName,
        String icdCode,
        LocalDate diagnosedAt,
        DiseaseStatus status,
        String notes
) {
}
