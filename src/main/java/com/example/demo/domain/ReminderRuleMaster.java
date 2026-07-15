package com.example.demo.domain;

import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.MatchTarget;
import com.example.demo.domain.enums.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사전 정의 리마인드 규칙(참조용, 읽기 전용). 테이블: reminder_rule_master
 */
@Entity
@Table(name = "reminder_rule_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "rule_code", nullable = false, unique = true)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_target", nullable = false)
    private MatchTarget matchTarget;

    @Column(name = "match_code")
    private String matchCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType;

    @Column(name = "frequency_value", nullable = false)
    private String frequencyValue;

    @Column(name = "message_template", nullable = false)
    private String messageTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_response", nullable = false)
    private ExpectedResponse expectedResponse;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
