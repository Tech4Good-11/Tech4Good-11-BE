package com.example.demo.dto.elder;

import com.example.demo.domain.enums.Gender;
import com.example.demo.domain.enums.Relationship;

import java.time.LocalDate;

public record ElderCreateRequest(
        String name,
        LocalDate birthDate,
        Gender gender,
        String phone,
        Relationship relationship
) {
}
