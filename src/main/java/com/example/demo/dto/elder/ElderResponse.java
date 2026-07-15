package com.example.demo.dto.elder;

import com.example.demo.domain.Elder;
import com.example.demo.domain.enums.Gender;
import com.example.demo.domain.enums.Relationship;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ElderResponse(
        Long id,
        String name,
        LocalDate birthDate,
        Gender gender,
        String phone,
        Relationship relationship,
        LocalDateTime createdAt
) {
    public static ElderResponse from(Elder elder, Relationship relationship) {
        return new ElderResponse(
                elder.getId(),
                elder.getName(),
                elder.getBirthDate(),
                elder.getGender(),
                elder.getPhone(),
                relationship,
                elder.getCreatedAt()
        );
    }
}
