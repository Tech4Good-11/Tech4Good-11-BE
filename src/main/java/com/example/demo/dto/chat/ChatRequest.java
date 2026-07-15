package com.example.demo.dto.chat;

import com.example.demo.domain.enums.ConversationPurpose;

import java.util.List;

/**
 * 챗봇 요청.
 * - message: 이번 사용자(어르신/보호자) 발화. 필수.
 * - history: 이전 대화 맥락(선택). role 은 user | assistant.
 * - purpose: 저장 시 대화 목적(선택, 기본 free).
 * - save: true 면 이번 대화를 agent_conversation 에 저장(선택, 기본 false).
 */
public record ChatRequest(
        String message,
        List<Turn> history,
        ConversationPurpose purpose,
        Boolean save
) {
    public record Turn(
            String role,
            String content
    ) {
    }
}
