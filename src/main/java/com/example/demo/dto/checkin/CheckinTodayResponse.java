package com.example.demo.dto.checkin;

import com.example.demo.domain.enums.ExpectedResponse;

import java.util.List;

/** 오늘의 문진 항목. */
public record CheckinTodayResponse(
        String ruleCode,
        String question,
        ExpectedResponse expectedResponse,
        List<String> scheduledTimes
) {
}
