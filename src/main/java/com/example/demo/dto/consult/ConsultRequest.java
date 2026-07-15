package com.example.demo.dto.consult;

import java.util.List;

/**
 * 자녀(보호자)의 상담 요청.
 * 어르신 대화(/chat)와 달리 저장되지 않고, 어르신의 건강 지표로 추출되지도 않는다.
 */
public record ConsultRequest(
        String message,
        List<Turn> history
) {
    public record Turn(
            String role,     // user(자녀) | assistant(AI)
            String content
    ) {
    }
}
