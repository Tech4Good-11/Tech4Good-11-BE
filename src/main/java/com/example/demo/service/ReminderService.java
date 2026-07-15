package com.example.demo.service;

import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.ReminderRuleMaster;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.MatchTarget;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.domain.enums.RuleType;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.dto.reminder.ReminderRuleResponse;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.repository.ReminderRuleMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 리마인드 규칙 매칭. 시니어의 질병(icd_code)·복약(atc_code) 실데이터를
 * reminder_rule_master 의 match_target/match_code 와 매칭해 산출.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderService {

    private final ReminderRuleMasterRepository ruleRepository;
    private final ElderDiseaseRepository diseaseRepository;
    private final ElderMedicationRepository medicationRepository;
    private final OwnershipService ownershipService;

    /** 규칙 마스터 목록(참조용). */
    public List<ReminderRuleResponse> listRules(RuleType ruleType, Boolean isActive) {
        List<ReminderRuleMaster> rules;
        if (ruleType != null && isActive != null) {
            rules = ruleRepository.findByRuleTypeAndIsActive(ruleType, isActive);
        } else if (ruleType != null) {
            rules = ruleRepository.findByRuleType(ruleType);
        } else if (isActive != null) {
            rules = ruleRepository.findByIsActive(isActive);
        } else {
            rules = ruleRepository.findAll();
        }
        return rules.stream().map(ReminderRuleResponse::from).toList();
    }

    /** 시니어에게 적용되는 리마인드(소유권 검증 포함). */
    public List<ElderReminderResponse> listElderReminders(Long userId, Long elderId) {
        String elderName = ownershipService.verifyAndGetElder(userId, elderId).getName();
        return matchRules(elderId, elderName);
    }

    /** 소유권 검증을 이미 한 호출자용(대시보드 등). */
    public List<ElderReminderResponse> matchRules(Long elderId, String elderName) {
        List<ElderDisease> diseases = diseaseRepository.findByElderIdAndStatus(elderId, DiseaseStatus.active);
        diseases.addAll(diseaseRepository.findByElderIdAndStatus(elderId, DiseaseStatus.managed));
        List<ElderMedication> meds = medicationRepository.findByElderIdAndStatus(elderId, MedicationStatus.active);

        List<ReminderRuleMaster> activeRules = ruleRepository.findByIsActive(true);
        List<ElderReminderResponse> result = new ArrayList<>();

        for (ReminderRuleMaster rule : activeRules) {
            ElderReminderResponse.MatchedBy matchedBy = evaluate(rule, diseases, meds);
            if (matchedBy == null) {
                continue;
            }
            String message = rule.getMessageTemplate().replace("{name}", elderName);
            result.add(new ElderReminderResponse(
                    rule.getRuleCode(),
                    rule.getRuleType(),
                    message,
                    rule.getFrequencyType(),
                    parseTimes(rule.getFrequencyType(), rule.getFrequencyValue()),
                    rule.getExpectedResponse(),
                    matchedBy
            ));
        }
        return result;
    }

    /** 규칙이 시니어에게 적용되는지 판정. 적용되면 MatchedBy, 아니면 null. */
    private ElderReminderResponse.MatchedBy evaluate(ReminderRuleMaster rule,
                                                     List<ElderDisease> diseases,
                                                     List<ElderMedication> meds) {
        MatchTarget target = rule.getMatchTarget();
        String code = rule.getMatchCode();

        if (target == MatchTarget.all) {
            // 대상 전체: 질병이나 복약이 하나라도 있으면 적용
            if (!diseases.isEmpty() || !meds.isEmpty()) {
                return new ElderReminderResponse.MatchedBy("all", null, null, null);
            }
            return null;
        }
        if (target == MatchTarget.medication) {
            for (ElderMedication m : meds) {
                if (code == null || code.equals(m.getAtcCode())) {
                    return new ElderReminderResponse.MatchedBy("medication", m.getAtcCode(), m.getMedicationName(), null);
                }
            }
            return null;
        }
        if (target == MatchTarget.disease) {
            for (ElderDisease d : diseases) {
                if (code == null || code.equals(d.getIcdCode())) {
                    return new ElderReminderResponse.MatchedBy("disease", d.getIcdCode(), null, d.getDiseaseName());
                }
            }
            return null;
        }
        return null;
    }

    /** frequency_value 를 시각 목록으로 파싱. daily="09:00,21:00", interval_hours="8", weekly="MON 09:00". */
    private List<String> parseTimes(FrequencyType type, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        if (type == FrequencyType.daily) {
            return Arrays.stream(value.split(",")).map(String::trim).toList();
        }
        // interval_hours / weekly 는 원본 값을 단일 항목으로 반환(스케줄 계산은 단순화)
        return List.of(value.trim());
    }
}
