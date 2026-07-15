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

import java.time.LocalDateTime;

/**
 * 시니어별 마크다운 건강 컨텍스트(시니어당 1개). 테이블: elder_health_note
 */
@Entity
@Table(name = "elder_health_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElderHealthNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false, unique = true)
    private Long elderId;

    @Column(name = "content_md", nullable = false)
    private String contentMd;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ElderHealthNote(Long elderId, String contentMd) {
        this.elderId = elderId;
        this.contentMd = contentMd;
    }

    public void update(String contentMd) {
        this.contentMd = contentMd;
    }
}
