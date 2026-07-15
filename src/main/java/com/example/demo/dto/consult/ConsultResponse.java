package com.example.demo.dto.consult;

/**
 * 자녀 상담 응답.
 * 상담은 기록으로 남기지 않으므로 conversationId 가 없다(어르신 대화 이력을 오염시키지 않기 위함).
 */
public record ConsultResponse(
        String reply
) {
}
