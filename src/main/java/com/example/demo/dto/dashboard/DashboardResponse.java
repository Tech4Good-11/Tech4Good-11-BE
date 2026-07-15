package com.example.demo.dto.dashboard;

import com.example.demo.dto.dailylog.DailyLogResponse;
import com.example.demo.dto.dailylog.MedicationIntakeResponse;
import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.dto.medication.MedicationResponse;
import com.example.demo.dto.reminder.ElderReminderResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 통합 대시보드 응답.
 * 기존 필드는 그대로 두고 대화에서 추출한 지표(dailyLog / todayMedications / healthScore)를 추가했다.
 */
public record DashboardResponse(
        ElderBrief elder,
        HealthNoteBrief healthNote,
        List<DiseaseResponse> diseases,
        List<MedicationResponse> medications,
        List<ElderReminderResponse> todayReminders,
        List<CheckinBrief> recentCheckins,
        /** 오늘의 수면/운동/AI요약/체크리스트. 값이 null 이면 '기록 없음'. */
        DailyLogResponse dailyLog,
        /** 오늘치 복용한 약(활성 약 전체 + 복용여부). taken=null 이면 미확인. */
        List<MedicationIntakeResponse> todayMedications,
        /** 서버가 계산한 건강 점수(대화 지표 기반). 근거 데이터가 없으면 score=null. */
        HealthScore healthScore
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

    /**
     * 건강 점수(0~100). 대화로 직접 얻는 값이 아니라 아래 지표로 서버가 계산한 파생값이다.
     * 각 항목은 근거 데이터가 없으면 null 이며, null 인 항목은 총점 평균에서 제외된다.
     * 모든 항목이 null 이면 score=null → 프론트는 '기록 없음'을 표시한다.
     */
    public record HealthScore(
            Integer score,            // 총점(사용 가능한 항목 평균)
            Integer medicationScore,  // 복약 순응도: 오늘 복용한 약 / 활성 약
            Integer sleepScore,       // 수면: 7시간 기준
            Integer exerciseScore,    // 운동: 30분 기준
            String comment            // 산출 근거 요약(사람이 읽는 설명)
    ) {
    }
}
