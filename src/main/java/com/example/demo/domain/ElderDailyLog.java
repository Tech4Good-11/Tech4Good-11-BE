package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 어르신 하루 생활 로그(어르신당 하루 1행). 테이블: elder_daily_log
 * 대화에서 추출하거나 보호자가 직접 입력한다.
 */
@Entity
@Table(name = "elder_daily_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElderDailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    /** 수면시간(시간, 자가보고). 모르면 null. */
    @Column(name = "sleep_hours")
    private BigDecimal sleepHours;

    /** 운동량(분, 자가보고). 걸음수 아님. 모르면 null. */
    @Column(name = "exercise_minutes")
    private Integer exerciseMinutes;

    /** AI 상담 요약(대화 한 줄 요약). */
    @Column(name = "condition_summary")
    private String conditionSummary;

    /** 체크리스트 응답 JSON. 예: {"HTN_MED_CHECK":"yes"} */
    @Column(name = "checklist_answers", columnDefinition = "json")
    private String checklistAnswers;

    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ElderDailyLog(Long elderId, LocalDate logDate, BigDecimal sleepHours, Integer exerciseMinutes,
                          String conditionSummary, String checklistAnswers, Long sourceConversationId) {
        this.elderId = elderId;
        this.logDate = logDate;
        this.sleepHours = sleepHours;
        this.exerciseMinutes = exerciseMinutes;
        this.conditionSummary = conditionSummary;
        this.checklistAnswers = checklistAnswers;
        this.sourceConversationId = sourceConversationId;
    }

    /** null 이 아닌 값만 덮어쓴다(대화 추출은 알아낸 값만 갱신). */
    public void patch(BigDecimal sleepHours, Integer exerciseMinutes, String conditionSummary,
                      String checklistAnswers, Long sourceConversationId) {
        if (sleepHours != null) {
            this.sleepHours = sleepHours;
        }
        if (exerciseMinutes != null) {
            this.exerciseMinutes = exerciseMinutes;
        }
        if (conditionSummary != null) {
            this.conditionSummary = conditionSummary;
        }
        if (checklistAnswers != null) {
            this.checklistAnswers = checklistAnswers;
        }
        if (sourceConversationId != null) {
            this.sourceConversationId = sourceConversationId;
        }
    }
}
