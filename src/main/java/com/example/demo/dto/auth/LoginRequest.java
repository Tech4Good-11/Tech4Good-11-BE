package com.example.demo.dto.auth;

public record LoginRequest(
        String email,
        String password
) {
}
