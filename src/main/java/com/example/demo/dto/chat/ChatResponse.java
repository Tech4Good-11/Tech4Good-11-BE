package com.example.demo.dto.chat;

/**
 * 챗봇 응답.
 * - reply: 에이전트('온기') 답변 텍스트.
 * - conversationId: save=true 로 저장했을 때 생성된 대화 id (아니면 null).
 */
public record ChatResponse(
        String reply,
        Long conversationId
) {
}
