package com.example.demo.dto.elder;

import com.example.demo.domain.enums.Gender;

import java.time.LocalDate;

/** 시니어 수정(relationship 제외) */
public record ElderUpdateRequest(
        String name,
        LocalDate birthDate,
        Gender gender,
        String phone
) {
}
