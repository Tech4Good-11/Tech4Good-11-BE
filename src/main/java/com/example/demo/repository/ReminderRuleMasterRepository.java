package com.example.demo.repository;

import com.example.demo.domain.ReminderRuleMaster;
import com.example.demo.domain.enums.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderRuleMasterRepository extends JpaRepository<ReminderRuleMaster, Integer> {
    List<ReminderRuleMaster> findByIsActive(Boolean isActive);
    List<ReminderRuleMaster> findByRuleType(RuleType ruleType);
    List<ReminderRuleMaster> findByRuleTypeAndIsActive(RuleType ruleType, Boolean isActive);
}
