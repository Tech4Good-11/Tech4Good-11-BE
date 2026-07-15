package com.example.demo.domain;

import com.example.demo.domain.enums.DiseaseStatus;
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
 * 시니어 질병 실데이터. 테이블: elder_disease
 */
@Entity
@Table(name = "elder_disease")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElderDisease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "disease_name", nullable = false)
    private String diseaseName;

    @Column(name = "icd_code")
    private String icdCode;

    @Column(name = "diagnosed_at")
    private LocalDate diagnosedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiseaseStatus status;

    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ElderDisease(Long elderId, String diseaseName, String icdCode,
                         LocalDate diagnosedAt, DiseaseStatus status, String notes) {
        this.elderId = elderId;
        this.diseaseName = diseaseName;
        this.icdCode = icdCode;
        this.diagnosedAt = diagnosedAt;
        this.status = status;
        this.notes = notes;
    }

    public void update(String diseaseName, String icdCode, LocalDate diagnosedAt,
                       DiseaseStatus status, String notes) {
        this.diseaseName = diseaseName;
        this.icdCode = icdCode;
        this.diagnosedAt = diagnosedAt;
        this.status = status;
        this.notes = notes;
    }
}
