package com.example.demo.dto.checkin;

import java.util.List;

public record CheckinSubmitRequest(
        List<Answer> answers
) {
    public record Answer(
            String ruleCode,
            String answer   // "yes" | "no"
    ) {
    }
}
