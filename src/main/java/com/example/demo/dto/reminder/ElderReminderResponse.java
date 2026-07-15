package com.example.demo.dto.reminder;

import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.RuleType;

import java.util.List;

/** 시니어에게 적용되는 리마인드(질병/복약 매칭 결과). */
public record ElderReminderResponse(
        String ruleCode,
        RuleType ruleType,
        String message,
        FrequencyType frequencyType,
        List<String> times,
        ExpectedResponse expectedResponse,
        MatchedBy matchedBy
) {
    /** 매칭 근거. */
    public record MatchedBy(
            String target,      // disease | medication | all
            String code,        // icd_code | atc_code | null
            String medicationName,
            String diseaseName
    ) {
    }
}
