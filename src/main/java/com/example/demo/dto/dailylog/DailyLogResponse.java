package com.example.demo.dto.dailylog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 하루 생활 로그 응답.
 * 값이 null 이면 "아직 대화/입력으로 알아내지 못함" = 프론트에서 '기록 없음' 표시.
 */
public record DailyLogResponse(
        Long elderId,
        LocalDate logDate,
        BigDecimal sleepHours,        // 수면시간(시간). 예: 6.5
        Integer exerciseMinutes,      // 운동량(분). 걸음수 아님
        String conditionSummary,      // AI 상담 요약
        List<ChecklistAnswer> checklist,
        Long sourceConversationId,
        LocalDateTime updatedAt
) {
    public record ChecklistAnswer(
            String ruleCode,
            String answer   // "yes" | "no"
    ) {
    }
}
