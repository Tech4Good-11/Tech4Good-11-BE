package com.example.demo.dto.conversation;

import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.enums.ConversationPurpose;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 대화 상세(transcript 포함). */
public record ConversationDetailResponse(
        Long id,
        Long elderId,
        ConversationPurpose purpose,
        JsonNode transcript,
        LocalDateTime createdAt
) {
    public static ConversationDetailResponse of(AgentConversation c, JsonNode transcript) {
        return new ConversationDetailResponse(
                c.getId(),
                c.getElderId(),
                c.getPurpose(),
                transcript,
                c.getCreatedAt()
        );
    }
}
