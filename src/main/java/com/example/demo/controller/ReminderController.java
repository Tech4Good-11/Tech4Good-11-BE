package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.domain.enums.RuleType;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.dto.reminder.ReminderRuleResponse;
import com.example.demo.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Reminders", description = "리마인드. 규칙 마스터(참조용)와 어르신별 적용 리마인드(대시보드 '오늘의 알림').")
@RestController
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "규칙 마스터 목록 (참조용)",
            description = "시스템에 사전 정의된 리마인드 규칙 전체(마스터). **주로 관리/디버그·규칙 확인용**이며 일반 사용자 화면엔 거의 쓰지 않는다. "
                    + "실제 어르신에게 뜨는 알림은 아래 '어르신 적용 리마인드' API 를 사용.")
    @GetMapping("/api/reminder-rules")
    public ApiResponse<List<ReminderRuleResponse>> listRules(@RequestParam(required = false) RuleType ruleType,
                                                             @RequestParam(required = false) Boolean isActive,
                                                             HttpSession session) {
        sessionUtil.currentUserId(session); // 로그인 필요
        return ApiResponse.success(reminderService.listRules(ruleType, isActive));
    }

    @Operation(summary = "이 시니어에게 적용되는 리마인드 (질병/복약 매칭)",
            description = "어르신의 질병(icd_code)·복약(atc_code)에 매칭되어 **실제로 적용되는 리마인드 목록**. "
                    + "메시지의 {name} 은 어르신 이름으로 치환되어 내려온다. **[대시보드 '오늘의 알림' / 알림 설정 화면]** 에 렌더.")
    @GetMapping("/api/elders/{elderId}/reminders")
    public ApiResponse<List<ElderReminderResponse>> elderReminders(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(reminderService.listElderReminders(userId, elderId));
    }
}
