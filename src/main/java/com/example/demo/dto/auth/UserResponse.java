package com.example.demo.dto.auth;

import com.example.demo.domain.User;

import java.time.LocalDateTime;

/** 보호자 응답 DTO. passwordHash 는 절대 포함하지 않는다. */
public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getCreatedAt()
        );
    }
}
