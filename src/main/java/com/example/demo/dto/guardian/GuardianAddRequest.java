package com.example.demo.dto.guardian;

import com.example.demo.domain.enums.Relationship;

public record GuardianAddRequest(
        String email,
        Relationship relationship
) {
}
