package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.chat.ChatRequest;
import com.example.demo.dto.chat.ChatResponse;
import com.example.demo.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "에이전트('온기') 챗봇. 어르신 건강 컨텍스트 기반 대화(OpenAI).")
@RestController
@RequestMapping("/api/elders/{elderId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "에이전트와 대화 (OpenAI)",
            description = "어르신의 질병·복약·건강노트를 시스템 컨텍스트로 넣어 AI 에이전트가 답변한다. "
                    + "**[대화/안부 화면]** 에서 사용. `message`(이번 발화)는 필수, `history`(이전 대화 맥락)는 선택. "
                    + "`save=true` 로 보내면 이번 대화를 대화 기록(agent_conversation)에 저장하고 conversationId 를 반환한다. "
                    + "\n\n⚠️ 서버에 `OPENAI_API_KEY` 환경변수가 설정돼 있어야 동작한다(미설정 시 500).")
    @PostMapping
    public ApiResponse<ChatResponse> chat(@PathVariable Long elderId,
                                          @RequestBody ChatRequest request,
                                          HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(chatService.chat(userId, elderId, request));
    }
}
