package com.example.demo.dto.conversation;

import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.enums.ConversationPurpose;

import java.time.LocalDateTime;

/** 대화 목록/생성 응답(transcript 제외). */
public record ConversationSummaryResponse(
        Long id,
        Long elderId,
        ConversationPurpose purpose,
        LocalDateTime createdAt
) {
    public static ConversationSummaryResponse from(AgentConversation c) {
        return new ConversationSummaryResponse(
                c.getId(),
                c.getElderId(),
                c.getPurpose(),
                c.getCreatedAt()
        );
    }
}
