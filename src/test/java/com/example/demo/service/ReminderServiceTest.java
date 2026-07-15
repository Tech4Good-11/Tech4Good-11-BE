package com.example.demo.service;

import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.ReminderRuleMaster;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.MatchTarget;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.domain.enums.RuleType;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.dto.reminder.ReminderRuleResponse;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.repository.ReminderRuleMasterRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ReminderService 규칙 매칭 단위 테스트.
 *
 * <p>핵심 회귀 방지: matchTarget=all 규칙은 질병/복약이 하나도 없어도 항상 매칭되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderService")
class ReminderServiceTest {

    private static final Long ELDER_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final String ELDER_NAME = "김순자";

    @Mock
    private ReminderRuleMasterRepository ruleRepository;
    @Mock
    private ElderDiseaseRepository diseaseRepository;
    @Mock
    private ElderMedicationRepository medicationRepository;
    @Mock
    private OwnershipService ownershipService;

    @InjectMocks
    private ReminderService reminderService;

    /** 실제 Spring Data JPA 는 가변 List 를 반환하므로 스텁도 가변 List 로 맞춘다. */
    private void givenDiseases(List<ElderDisease> active, List<ElderDisease> managed) {
        when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active))
                .thenReturn(new ArrayList<>(active));
        when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.managed))
                .thenReturn(new ArrayList<>(managed));
    }

    private void givenMedications(List<ElderMedication> meds) {
        when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                .thenReturn(new ArrayList<>(meds));
    }

    private void givenActiveRules(ReminderRuleMaster... rules) {
        when(ruleRepository.findByIsActive(true)).thenReturn(new ArrayList<>(List.of(rules)));
    }

    private static ReminderRuleMaster hydrationRuleForAll() {
        return Fixtures.rule("WATER_ALL", RuleType.hydration, MatchTarget.all, null,
                FrequencyType.interval_hours, "3", "{name} 어르신, 물 한 잔 드세요.", ExpectedResponse.yes_no);
    }

    @Nested
    @DisplayName("matchTarget=all")
    class MatchTargetAll {

        @Test
        @DisplayName("질병·복약이 전혀 없어도 항상 매칭된다 (회귀 방지)")
        void should_match_rule_when_elder_has_no_disease_and_no_medication() {
            // Arrange: 아무 질병도 약도 등록되지 않은 어르신
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(hydrationRuleForAll());

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).ruleCode()).isEqualTo("WATER_ALL");
        }

        @Test
        @DisplayName("매칭 근거는 target=all 이고 코드/이름은 비어 있다")
        void should_report_matchedBy_all_when_matchTarget_is_all() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(hydrationRuleForAll());

            // Act
            ElderReminderResponse reminder = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0);

            // Assert
            assertThat(reminder.matchedBy()).isEqualTo(
                    new ElderReminderResponse.MatchedBy("all", null, null, null));
        }

        @Test
        @DisplayName("질병·복약이 있어도 중복 없이 한 번만 매칭된다")
        void should_match_once_when_elder_has_diseases_and_medications() {
            // Arrange
            givenDiseases(List.of(Fixtures.disease(1L, ELDER_ID, "고혈압", "I10", DiseaseStatus.active)), List.of());
            givenMedications(List.of(Fixtures.medication(1L, ELDER_ID, "아모디핀", "C08CA01")));
            givenActiveRules(hydrationRuleForAll());

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("matchTarget=medication")
    class MatchTargetMedication {

        private ReminderRuleMaster ruleWithCode(String atcCode) {
            return Fixtures.rule("MED_CHECK", RuleType.medication, MatchTarget.medication, atcCode,
                    FrequencyType.daily, "09:00", "{name} 어르신, 약 드셨어요?", ExpectedResponse.yes_no);
        }

        @Test
        @DisplayName("atcCode 가 일치하면 매칭되고 약 이름을 근거로 싣는다")
        void should_match_and_expose_medication_name_when_atcCode_matches() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01")));
            givenActiveRules(ruleWithCode("C08CA01"));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).matchedBy()).isEqualTo(
                    new ElderReminderResponse.MatchedBy("medication", "C08CA01", "아모디핀", null));
        }

        @Test
        @DisplayName("atcCode 가 다르면 매칭되지 않는다")
        void should_not_match_when_atcCode_differs() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01")));
            givenActiveRules(ruleWithCode("A10BA02"));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matchCode 가 null 이면 약이 있는 어르신 전체에 적용된다")
        void should_match_any_medication_when_matchCode_is_null() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of(Fixtures.medication(7L, ELDER_ID, "메트포르민", "A10BA02")));
            givenActiveRules(ruleWithCode(null));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).matchedBy().medicationName()).isEqualTo("메트포르민");
        }

        @Test
        @DisplayName("약이 하나도 없으면 matchCode 가 null 이어도 매칭되지 않는다")
        void should_not_match_when_elder_has_no_medication() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(ruleWithCode(null));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 약 중 코드가 일치하는 첫 약을 근거로 삼는다")
        void should_match_first_matching_medication_when_multiple_medications_exist() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of(
                    Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01"),
                    Fixtures.medication(8L, ELDER_ID, "메트포르민", "A10BA02")));
            givenActiveRules(ruleWithCode("A10BA02"));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result.get(0).matchedBy().medicationName()).isEqualTo("메트포르민");
        }
    }

    @Nested
    @DisplayName("matchTarget=disease")
    class MatchTargetDisease {

        private ReminderRuleMaster ruleWithCode(String icdCode) {
            return Fixtures.rule("BP_CHECK", RuleType.vital_check, MatchTarget.disease, icdCode,
                    FrequencyType.daily, "08:00", "{name} 어르신, 혈압 재셨어요?", ExpectedResponse.yes_no);
        }

        @Test
        @DisplayName("icdCode 가 일치하면 매칭되고 질병 이름을 근거로 싣는다")
        void should_match_and_expose_disease_name_when_icdCode_matches() {
            // Arrange
            givenDiseases(List.of(Fixtures.disease(3L, ELDER_ID, "고혈압", "I10", DiseaseStatus.active)), List.of());
            givenMedications(List.of());
            givenActiveRules(ruleWithCode("I10"));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).matchedBy()).isEqualTo(
                    new ElderReminderResponse.MatchedBy("disease", "I10", null, "고혈압"));
        }

        @Test
        @DisplayName("icdCode 가 다르면 매칭되지 않는다")
        void should_not_match_when_icdCode_differs() {
            // Arrange
            givenDiseases(List.of(Fixtures.disease(3L, ELDER_ID, "고혈압", "I10", DiseaseStatus.active)), List.of());
            givenMedications(List.of());
            givenActiveRules(ruleWithCode("E11"));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("managed 상태 질병도 매칭 대상에 포함된다")
        void should_match_when_disease_status_is_managed() {
            // Arrange: active 는 없고 managed 만 있는 상황
            givenDiseases(List.of(), List.of(Fixtures.disease(4L, ELDER_ID, "당뇨", "E11", DiseaseStatus.managed)));
            givenMedications(List.of());
            givenActiveRules(ruleWithCode("E11"));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).matchedBy().diseaseName()).isEqualTo("당뇨");
        }

        @Test
        @DisplayName("matchCode 가 null 이면 질병이 있는 어르신 전체에 적용된다")
        void should_match_any_disease_when_matchCode_is_null() {
            // Arrange
            givenDiseases(List.of(Fixtures.disease(3L, ELDER_ID, "관절염", "M19", DiseaseStatus.active)), List.of());
            givenMedications(List.of());
            givenActiveRules(ruleWithCode(null));

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result.get(0).matchedBy().diseaseName()).isEqualTo("관절염");
        }
    }

    @Nested
    @DisplayName("메시지 템플릿")
    class MessageTemplate {

        @Test
        @DisplayName("{name} 자리표시자를 어르신 이름으로 치환한다")
        void should_replace_name_placeholder_when_template_contains_it() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(hydrationRuleForAll());

            // Act
            ElderReminderResponse reminder = reminderService.matchRules(ELDER_ID, "박영수").get(0);

            // Assert
            assertThat(reminder.message()).isEqualTo("박영수 어르신, 물 한 잔 드세요.");
        }

        @Test
        @DisplayName("{name} 이 없는 템플릿은 원문 그대로 사용한다")
        void should_keep_template_as_is_when_no_placeholder() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(Fixtures.rule("MEAL", RuleType.meal, MatchTarget.all, null,
                    FrequencyType.daily, "12:00", "식사 하셨어요?", ExpectedResponse.yes_no));

            // Act
            ElderReminderResponse reminder = reminderService.matchRules(ELDER_ID, "박영수").get(0);

            // Assert
            assertThat(reminder.message()).isEqualTo("식사 하셨어요?");
        }
    }

    @Nested
    @DisplayName("parseTimes")
    class ParseTimes {

        private void arrangeRule(FrequencyType type, String value) {
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(Fixtures.rule("R", RuleType.custom, MatchTarget.all, null,
                    type, value, "안내", ExpectedResponse.none));
        }

        @Test
        @DisplayName("daily 는 콤마로 분리해 여러 시각으로 반환한다")
        void should_split_by_comma_when_frequencyType_is_daily() {
            // Arrange
            arrangeRule(FrequencyType.daily, "09:00,21:00");

            // Act
            List<String> times = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0).times();

            // Assert
            assertThat(times).containsExactly("09:00", "21:00");
        }

        @Test
        @DisplayName("daily 항목의 앞뒤 공백을 제거한다")
        void should_trim_each_time_when_frequencyType_is_daily() {
            // Arrange
            arrangeRule(FrequencyType.daily, " 09:00 , 21:00 ");

            // Act
            List<String> times = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0).times();

            // Assert
            assertThat(times).containsExactly("09:00", "21:00");
        }

        @Test
        @DisplayName("daily 값이 하나면 단일 항목으로 반환한다")
        void should_return_single_time_when_daily_has_one_value() {
            // Arrange
            arrangeRule(FrequencyType.daily, "09:00");

            // Act
            List<String> times = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0).times();

            // Assert
            assertThat(times).containsExactly("09:00");
        }

        @Test
        @DisplayName("interval_hours 는 원본 값을 단일 항목으로 반환한다")
        void should_return_raw_value_when_frequencyType_is_interval_hours() {
            // Arrange
            arrangeRule(FrequencyType.interval_hours, "8");

            // Act
            List<String> times = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0).times();

            // Assert
            assertThat(times).containsExactly("8");
        }

        @Test
        @DisplayName("weekly 는 콤마가 없는 원본 값을 단일 항목으로 반환한다")
        void should_return_raw_value_when_frequencyType_is_weekly() {
            // Arrange
            arrangeRule(FrequencyType.weekly, "MON 09:00");

            // Act
            List<String> times = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0).times();

            // Assert
            assertThat(times).containsExactly("MON 09:00");
        }

        @Test
        @DisplayName("frequencyValue 가 공백이면 빈 목록을 반환한다")
        void should_return_empty_list_when_frequencyValue_is_blank() {
            // Arrange
            arrangeRule(FrequencyType.daily, "   ");

            // Act
            List<String> times = reminderService.matchRules(ELDER_ID, ELDER_NAME).get(0).times();

            // Assert
            assertThat(times).isEmpty();
        }
    }

    @Nested
    @DisplayName("listElderReminders")
    class ListElderReminders {

        @Test
        @DisplayName("소유권 검증을 거친 뒤 해당 어르신 이름으로 매칭한다")
        void should_verify_ownership_and_use_elder_name_when_listing() {
            // Arrange
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "최말순"));
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(hydrationRuleForAll());

            // Act
            List<ElderReminderResponse> result = reminderService.listElderReminders(USER_ID, ELDER_ID);

            // Assert
            verify(ownershipService).verifyAndGetElder(USER_ID, ELDER_ID);
            assertThat(result.get(0).message()).isEqualTo("최말순 어르신, 물 한 잔 드세요.");
        }
    }

    @Nested
    @DisplayName("listRules")
    class ListRules {

        private final ReminderRuleMaster sample = hydrationRuleForAll();

        @Test
        @DisplayName("ruleType 과 isActive 가 모두 있으면 두 조건으로 조회한다")
        void should_query_by_type_and_active_when_both_given() {
            // Arrange
            when(ruleRepository.findByRuleTypeAndIsActive(RuleType.hydration, true)).thenReturn(List.of(sample));

            // Act
            List<ReminderRuleResponse> result = reminderService.listRules(RuleType.hydration, true);

            // Assert
            assertThat(result).hasSize(1);
            verify(ruleRepository).findByRuleTypeAndIsActive(RuleType.hydration, true);
        }

        @Test
        @DisplayName("ruleType 만 있으면 타입으로만 조회한다")
        void should_query_by_type_when_only_type_given() {
            // Arrange
            when(ruleRepository.findByRuleType(RuleType.hydration)).thenReturn(List.of(sample));

            // Act
            reminderService.listRules(RuleType.hydration, null);

            // Assert
            verify(ruleRepository).findByRuleType(RuleType.hydration);
        }

        @Test
        @DisplayName("isActive 만 있으면 활성 여부로만 조회한다")
        void should_query_by_active_when_only_active_given() {
            // Arrange
            when(ruleRepository.findByIsActive(false)).thenReturn(List.of());

            // Act
            reminderService.listRules(null, false);

            // Assert
            verify(ruleRepository).findByIsActive(false);
        }

        @Test
        @DisplayName("조건이 없으면 전체를 조회한다")
        void should_query_all_when_no_filter_given() {
            // Arrange
            when(ruleRepository.findAll()).thenReturn(List.of(sample));

            // Act
            List<ReminderRuleResponse> result = reminderService.listRules(null, null);

            // Assert
            verify(ruleRepository).findAll();
            assertThat(result.get(0).ruleCode()).isEqualTo("WATER_ALL");
        }

        @Test
        @DisplayName("규칙 목록 조회는 소유권 검증을 하지 않는다(참조용 마스터)")
        void should_not_verify_ownership_when_listing_rules() {
            // Arrange
            when(ruleRepository.findAll()).thenReturn(List.of(sample));

            // Act
            reminderService.listRules(null, null);

            // Assert
            verifyNoInteractions(ownershipService);
        }
    }

    @Nested
    @DisplayName("비활성 규칙")
    class InactiveRules {

        @Test
        @DisplayName("활성 규칙만 조회하므로 비활성 규칙은 결과에 없다")
        void should_only_consider_active_rules_when_matching() {
            // Arrange
            givenDiseases(List.of(), List.of());
            givenMedications(List.of());
            givenActiveRules(); // 활성 규칙 없음

            // Act
            List<ElderReminderResponse> result = reminderService.matchRules(ELDER_ID, ELDER_NAME);

            // Assert
            assertThat(result).isEmpty();
            verify(ruleRepository).findByIsActive(true);
        }
    }
}
