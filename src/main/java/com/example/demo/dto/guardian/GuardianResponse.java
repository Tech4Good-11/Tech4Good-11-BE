package com.example.demo.dto.guardian;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.Relationship;

public record GuardianResponse(
        Long userId,
        String email,
        String name,
        String phone,
        Relationship relationship
) {
    public static GuardianResponse of(User user, Relationship relationship) {
        return new GuardianResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                relationship
        );
    }
}
