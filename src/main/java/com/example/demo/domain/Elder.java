package com.example.demo.domain;

import com.example.demo.domain.enums.Gender;
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
 * 시니어(어르신) = 관리 대상. 테이블: elders
 */
@Entity
@Table(name = "elders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Elder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String phone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Elder(String name, LocalDate birthDate, Gender gender, String phone) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
    }

    public void update(String name, LocalDate birthDate, Gender gender, String phone) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
    }
}
