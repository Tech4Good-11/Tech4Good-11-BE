package com.example.demo.dto.conversation;

import com.example.demo.domain.enums.ConversationPurpose;
import tools.jackson.databind.JsonNode;

/** transcript 는 임의 JSON 배열/객체를 그대로 받는다. */
public record ConversationCreateRequest(
        ConversationPurpose purpose,
        JsonNode transcript
) {
}
