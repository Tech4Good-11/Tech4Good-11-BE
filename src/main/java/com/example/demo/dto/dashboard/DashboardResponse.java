package com.example.demo.dto.dashboard;

import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.dto.medication.MedicationResponse;
import com.example.demo.dto.reminder.ElderReminderResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 통합 대시보드 응답. */
public record DashboardResponse(
        ElderBrief elder,
        HealthNoteBrief healthNote,
        List<DiseaseResponse> diseases,
        List<MedicationResponse> medications,
        List<ElderReminderResponse> todayReminders,
        List<CheckinBrief> recentCheckins
) {
    public record ElderBrief(
            Long id,
            String name,
            LocalDate birthDate,
            String gender
    ) {
    }

    public record HealthNoteBrief(
            String contentMd,
            LocalDateTime updatedAt
    ) {
    }

    public record CheckinBrief(
            Long conversationId,
            String purpose,
            LocalDateTime createdAt,
            String summary
    ) {
    }
}
