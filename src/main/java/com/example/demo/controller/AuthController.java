package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.domain.User;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.SignupRequest;
import com.example.demo.dto.auth.UserResponse;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 / 보호자(자녀) 계정. 로그인 화면·앱 진입 시 세션 확인에 사용.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "회원가입",
            description = "보호자(자녀) 계정을 생성한다. **[회원가입 화면]** 에서 이메일/비밀번호/이름을 입력받아 호출. "
                    + "성공하면 로그인 화면으로 이동시키면 된다. 이메일이 이미 있으면 400(INVALID_INPUT). "
                    + "비밀번호는 서버에서 BCrypt 로 해싱되어 저장되며 응답에 노출되지 않는다.")
    @PostMapping("/signup")
    public ApiResponse<UserResponse> signup(@RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(summary = "로그인 (세션 쿠키 발급)",
            description = "이메일/비밀번호로 인증하고 **세션 쿠키(SESSION)** 를 발급한다. "
                    + "프론트는 이후 모든 인증 API 요청에 이 쿠키를 함께 보내야 한다(axios/fetch 는 `withCredentials`/`credentials:'include'`). "
                    + "**[로그인 화면]** 에서 사용. 자격 증명이 틀리면 401(UNAUTHORIZED).")
    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@RequestBody LoginRequest request, HttpSession session) {
        User user = authService.authenticate(request);
        sessionUtil.login(session, user.getId());
        return ApiResponse.success(UserResponse.from(user));
    }

    @Operation(summary = "로그아웃 (세션 무효화)",
            description = "현재 세션을 무효화한다. **[설정/헤더의 로그아웃 버튼]** 에서 호출. "
                    + "호출 후 프론트는 로컬 상태를 비우고 로그인 화면으로 보낸다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        sessionUtil.currentUserId(session); // 로그인 필요
        sessionUtil.logout(session);
        return ApiResponse.<Void>success("로그아웃되었습니다.", null);
    }

    @Operation(summary = "내 정보",
            description = "현재 로그인한 보호자 정보를 반환한다. **앱 진입 시 로그인 상태(세션 유효성) 확인** 과 "
                    + "헤더/마이페이지의 내 프로필 표시에 사용. 세션이 없거나 만료면 401 → 로그인 화면으로 유도.")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(authService.getById(userId));
    }
}
