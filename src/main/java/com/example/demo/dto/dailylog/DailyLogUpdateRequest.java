package com.example.demo.dto.dailylog;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 하루 생활 로그 수동 저장/수정 요청.
 * logDate 를 생략하면 오늘. null 필드는 변경하지 않는다(부분 수정).
 */
public record DailyLogUpdateRequest(
        LocalDate logDate,
        BigDecimal sleepHours,
        Integer exerciseMinutes,
        String conditionSummary
) {
}
