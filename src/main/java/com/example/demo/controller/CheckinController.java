package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.checkin.CheckinSubmitRequest;
import com.example.demo.dto.checkin.CheckinSubmitResponse;
import com.example.demo.dto.checkin.CheckinTodayResponse;
import com.example.demo.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Check-in", description = "일일 Yes/No 안부문진. [안부 확인 화면]에서 오늘 질문을 받아 응답을 제출.")
@RestController
@RequestMapping("/api/elders/{elderId}/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "오늘의 문진 항목 (리마인드 규칙 매칭)",
            description = "오늘 어르신에게 물어볼 안부 질문 목록을 반환한다(어르신의 질병/복약에 매칭된 리마인드 규칙 기반). "
                    + "**[안부 확인 화면]** 에서 이 리스트를 예/아니오 질문 UI 로 렌더. 각 항목의 ruleCode 를 응답 제출 때 그대로 돌려준다.")
    @GetMapping("/today")
    public ApiResponse<List<CheckinTodayResponse>> today(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(checkinService.today(userId, elderId));
    }

    @Operation(summary = "문진 응답 제출 (daily_checkin 대화로 저장)",
            description = "예/아니오 응답을 제출한다(ruleCode + answer 배열). 응답은 daily_checkin 대화로 저장되어 "
                    + "대시보드의 '최근 안부'와 대화 이력에 반영된다. **[안부 확인 화면]의 제출 버튼**.")
    @PostMapping
    public ApiResponse<CheckinSubmitResponse> submit(@PathVariable Long elderId,
                                                     @RequestBody CheckinSubmitRequest request,
                                                     HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(checkinService.submit(userId, elderId, request));
    }
}
