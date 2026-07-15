package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.consult.ConsultRequest;
import com.example.demo.dto.consult.ConsultResponse;
import com.example.demo.service.ConsultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Consult", description = "자녀(보호자)가 부모님 상태에 대해 AI와 상담. 어르신 대화(Chat)와 구분됨.")
@RestController
@RequestMapping("/api/elders/{elderId}/consult")
@RequiredArgsConstructor
public class ConsultController {

    private final ConsultService consultService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "부모님 상태에 대해 AI와 상담 (자녀용)",
            description = "**[자녀의 AI 상담 화면]**. 어르신이 남긴 기록(최근 대화·건강노트·질병·복약·수면/운동)을 "
                    + "근거로 AI가 자녀에게 답한다. 예: \"어머니가 머리 아프다고 하셨던데 어떻게 할까요?\" → "
                    + "AI가 어르신의 실제 대화 기록을 참고해 답변.\n\n"
                    + "**[Chat] `/chat` 과 반드시 구분할 것:**\n"
                    + "- `/chat` = **어르신 본인**이 AI와 나누는 대화. 저장되고, 발화에서 건강 지표(수면·운동·복약)가 추출된다.\n"
                    + "- `/consult` = **자녀**가 AI와 나누는 상담. 어르신을 3인칭으로 지칭하고, "
                    + "**저장되지 않으며 건강 지표로 추출되지도 않는다**(자녀 발화는 어르신의 자가보고가 아니므로).\n\n"
                    + "`message`(이번 질문)는 필수, `history`(이전 상담 맥락)는 선택. "
                    + "저장하지 않으므로 대화 맥락이 필요하면 프론트가 `history` 로 넘겨야 한다.\n\n"
                    + "⚠️ 서버에 `OPENAI_API_KEY` 필요(미설정 시 500).")
    @PostMapping
    public ApiResponse<ConsultResponse> consult(@PathVariable Long elderId,
                                                @RequestBody ConsultRequest request,
                                                HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(consultService.consult(userId, elderId, request));
    }
}
