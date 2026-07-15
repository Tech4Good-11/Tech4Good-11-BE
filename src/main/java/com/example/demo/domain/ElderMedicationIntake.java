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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 어르신 복약 여부(약 1개당 하루 1행). 테이블: elder_medication_intake
 * 대화("약 드셨어요?") 또는 체크리스트 체크로 기록된다.
 */
@Entity
@Table(name = "elder_medication_intake")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElderMedicationIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Column(name = "intake_date", nullable = false)
    private LocalDate intakeDate;

    @Column(nullable = false)
    private Boolean taken;

    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ElderMedicationIntake(Long elderId, Long medicationId, LocalDate intakeDate,
                                  Boolean taken, Long sourceConversationId) {
        this.elderId = elderId;
        this.medicationId = medicationId;
        this.intakeDate = intakeDate;
        this.taken = taken;
        this.sourceConversationId = sourceConversationId;
    }

    public void update(Boolean taken, Long sourceConversationId) {
        this.taken = taken;
        if (sourceConversationId != null) {
            this.sourceConversationId = sourceConversationId;
        }
    }
}
