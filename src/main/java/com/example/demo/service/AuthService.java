package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.User;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.SignupRequest;
import com.example.demo.dto.auth.UserResponse;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일/비밀번호/이름은 필수입니다.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 가입된 이메일입니다.");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    /** 로그인 검증 후 사용자 반환(세션 저장은 컨트롤러에서). */
    public User authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email() == null ? "" : request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));
        if (request.password() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return user;
    }

    public UserResponse getById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return UserResponse.from(user);
    }
}
