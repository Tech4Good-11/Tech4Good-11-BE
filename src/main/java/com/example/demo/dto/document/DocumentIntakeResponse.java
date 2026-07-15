package com.example.demo.dto.document;

import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.dto.medication.MedicationResponse;

import java.util.List;

/** 진단서/처방전 처리 결과. */
public record DocumentIntakeResponse(
        Long conversationId,
        String docType,   // diagnosis | prescription
        List<MedicationResponse> extractedMedications,
        List<DiseaseResponse> extractedDiseases,
        boolean healthNoteUpdated
) {
}
