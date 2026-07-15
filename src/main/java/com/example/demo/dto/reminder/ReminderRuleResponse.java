package com.example.demo.dto.reminder;

import com.example.demo.domain.ReminderRuleMaster;
import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.MatchTarget;
import com.example.demo.domain.enums.RuleType;

/** 규칙 마스터 응답(참조용). */
public record ReminderRuleResponse(
        Integer id,
        String ruleCode,
        RuleType ruleType,
        MatchTarget matchTarget,
        String matchCode,
        FrequencyType frequencyType,
        String frequencyValue,
        String messageTemplate,
        ExpectedResponse expectedResponse,
        Boolean isActive
) {
    public static ReminderRuleResponse from(ReminderRuleMaster r) {
        return new ReminderRuleResponse(
                r.getId(),
                r.getRuleCode(),
                r.getRuleType(),
                r.getMatchTarget(),
                r.getMatchCode(),
                r.getFrequencyType(),
                r.getFrequencyValue(),
                r.getMessageTemplate(),
                r.getExpectedResponse(),
                r.getIsActive()
        );
    }
}
