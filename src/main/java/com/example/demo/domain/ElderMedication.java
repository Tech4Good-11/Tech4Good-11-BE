package com.example.demo.domain;

import com.example.demo.domain.enums.MedicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 시니어 복약 실데이터. 테이블: elder_medication
 */
@Entity
@Table(name = "elder_medication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElderMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(name = "atc_code")
    private String atcCode;

    private String dosage;

    @Column(name = "interval_hours")
    private Integer intervalHours;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicationStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ElderMedication(Long elderId, String medicationName, String atcCode, String dosage,
                            Integer intervalHours, LocalDate startDate, LocalDate endDate,
                            MedicationStatus status) {
        this.elderId = elderId;
        this.medicationName = medicationName;
        this.atcCode = atcCode;
        this.dosage = dosage;
        this.intervalHours = intervalHours;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public void update(String medicationName, String atcCode, String dosage, Integer intervalHours,
                       LocalDate startDate, LocalDate endDate, MedicationStatus status) {
        this.medicationName = medicationName;
        this.atcCode = atcCode;
        this.dosage = dosage;
        this.intervalHours = intervalHours;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }
}
