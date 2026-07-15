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

@Tag(name = "Chat", description = "⚠️ 어르신 본인이 AI와 나누는 대화. 자녀의 상담은 [Consult] /consult 를 사용할 것.")
@RestController
@RequestMapping("/api/elders/{elderId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "어르신이 AI와 대화 (어르신 전용)",
            description = "**[어르신의 대화/안부 화면]**. 어르신의 질병·복약·건강노트를 컨텍스트로 넣어 "
                    + "AI 가 어르신에게 다정한 말벗으로 답한다(어르신을 2인칭으로 지칭).\n\n"
                    + "**🔴 자녀가 이 API 를 쓰면 안 된다.** 자녀가 부모님 상태를 상담하려면 **[Consult] `/consult`** 를 사용할 것. "
                    + "이 API 는 발화자를 어르신으로 간주하므로, 자녀가 쓰면 자녀의 말이 어르신 발화로 저장되고 "
                    + "어르신의 건강 지표로 잘못 추출된다.\n\n"
                    + "`message`(이번 발화)는 필수, `history`(이전 대화 맥락)는 선택. "
                    + "`save=true` 로 보내면 대화를 저장하고 **발화에서 수면·운동·복약·질병 상황을 자동 추출**해 대시보드에 반영한다.\n\n"
                    + "⚠️ 서버에 `OPENAI_API_KEY` 환경변수가 설정돼 있어야 동작한다(미설정 시 500).")
    @PostMapping
    public ApiResponse<ChatResponse> chat(@PathVariable Long elderId,
                                          @RequestBody ChatRequest request,
                                          HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(chatService.chat(userId, elderId, request));
    }
}
