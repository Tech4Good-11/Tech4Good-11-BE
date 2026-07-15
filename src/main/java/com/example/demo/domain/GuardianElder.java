package com.example.demo.domain;

import com.example.demo.domain.enums.Relationship;
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

import java.time.LocalDateTime;

/**
 * 보호자-시니어 M:N 보호관계. 테이블: guardian_elder
 */
@Entity
@Table(name = "guardian_elder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianElder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Relationship relationship;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private GuardianElder(Long userId, Long elderId, Relationship relationship) {
        this.userId = userId;
        this.elderId = elderId;
        this.relationship = relationship;
    }
}
