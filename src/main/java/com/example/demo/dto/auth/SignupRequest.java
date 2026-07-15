package com.example.demo.dto.auth;

public record SignupRequest(
        String email,
        String password,
        String name,
        String phone
) {
}
