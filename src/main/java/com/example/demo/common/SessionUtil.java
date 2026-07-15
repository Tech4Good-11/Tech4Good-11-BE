package com.example.demo.common;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 세션 기반 인증 헬퍼.
 * 로그인 성공 시 세션에 userId 저장, 이후 요청에서 현재 로그인 사용자 조회.
 */
@Component
public class SessionUtil {

    public static final String USER_ID = "USER_ID";

    /** 로그인 처리: 세션에 userId 저장 */
    public void login(HttpSession session, Long userId) {
        session.setAttribute(USER_ID, userId);
    }

    /** 로그아웃 처리: 세션 무효화 */
    public void logout(HttpSession session) {
        session.invalidate();
    }

    /** 현재 로그인 사용자 id. 없으면 UNAUTHORIZED. */
    public Long currentUserId(HttpSession session) {
        Object userId = (session == null) ? null : session.getAttribute(USER_ID);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return (Long) userId;
    }
}
