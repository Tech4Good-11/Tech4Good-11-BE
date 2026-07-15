package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.ElderDailyLog;
import com.example.demo.domain.ElderMedicationIntake;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.domain.enums.RuleType;
import com.example.demo.dto.checkin.CheckinSubmitRequest;
import com.example.demo.dto.dailylog.DailyLogResponse;
import com.example.demo.dto.dailylog.DailyLogUpdateRequest;
import com.example.demo.dto.dailylog.MedicationIntakeRequest;
import com.example.demo.dto.dailylog.MedicationIntakeResponse;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDailyLogRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderMedicationIntakeRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DailyLogService 단위 테스트.
 *
 * <p>ObjectMapper 는 순수 변환 의존성이라 모킹하지 않고 실제 인스턴스를 사용한다(Jackson 3).
 * OpenAI 호출과 리포지토리만 모킹한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLogService")
class DailyLogServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ELDER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now();

    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private OwnershipService ownershipService;
    @Mock
    private ElderDailyLogRepository dailyLogRepository;
    @Mock
    private ElderMedicationIntakeRepository intakeRepository;
    @Mock
    private ElderMedicationRepository medicationRepository;
    @Mock
    private ElderDiseaseRepository diseaseRepository;
    @Mock
    private AgentConversationRepository conversationRepository;
    @Mock
    private ReminderService reminderService;

    private DailyLogService dailyLogService;

    @BeforeEach
    void setUp() {
        // 실제 ObjectMapper 주입을 위해 수동 조립한다(@InjectMocks 는 모의 객체만 주입).
        dailyLogService = new DailyLogService(
                openAiClient, ownershipService, dailyLogRepository, intakeRepository,
                medicationRepository, diseaseRepository, conversationRepository,
                reminderService, new ObjectMapper());
    }

    private ArgumentCaptor<ElderDailyLog> captureSavedLog() {
        ArgumentCaptor<ElderDailyLog> captor = ArgumentCaptor.forClass(ElderDailyLog.class);
        verify(dailyLogRepository).save(captor.capture());
        return captor;
    }

    private ArgumentCaptor<ElderMedicationIntake> captureSavedIntake() {
        ArgumentCaptor<ElderMedicationIntake> captor = ArgumentCaptor.forClass(ElderMedicationIntake.class);
        verify(intakeRepository).save(captor.capture());
        return captor;
    }

    @Nested
    @DisplayName("readLog")
    class ReadLog {

        @Test
        @DisplayName("로그가 없으면 모든 값이 null 인 빈 로그를 반환한다")
        void should_return_empty_log_when_no_record_exists() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            DailyLogResponse result = dailyLogService.readLog(ELDER_ID, TODAY);

            // Assert
            assertThat(result.sleepHours()).isNull();
            assertThat(result.exerciseMinutes()).isNull();
            assertThat(result.conditionSummary()).isNull();
            assertThat(result.checklist()).isEmpty();
        }

        @Test
        @DisplayName("저장된 로그를 응답 DTO 로 변환한다")
        void should_map_entity_to_response_when_record_exists() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.of(
                    Fixtures.dailyLog(1L, ELDER_ID, TODAY, new BigDecimal("6.5"), 30, "컨디션 양호", null)));

            // Act
            DailyLogResponse result = dailyLogService.readLog(ELDER_ID, TODAY);

            // Assert
            assertThat(result.sleepHours()).isEqualByComparingTo("6.5");
            assertThat(result.exerciseMinutes()).isEqualTo(30);
            assertThat(result.conditionSummary()).isEqualTo("컨디션 양호");
        }

        @Test
        @DisplayName("체크리스트 JSON 을 응답 항목으로 파싱한다")
        void should_parse_checklist_json_into_answers() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.of(
                    Fixtures.dailyLog(1L, ELDER_ID, TODAY, null, null, null,
                            "{\"HTN_MED_CHECK\":\"yes\",\"WATER\":\"no\"}")));

            // Act
            DailyLogResponse result = dailyLogService.readLog(ELDER_ID, TODAY);

            // Assert
            assertThat(result.checklist()).containsExactly(
                    new DailyLogResponse.ChecklistAnswer("HTN_MED_CHECK", "yes"),
                    new DailyLogResponse.ChecklistAnswer("WATER", "no"));
        }

        @Test
        @DisplayName("체크리스트 JSON 이 깨져 있어도 예외 없이 빈 목록을 반환한다")
        void should_return_empty_checklist_when_json_is_malformed() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.of(
                    Fixtures.dailyLog(1L, ELDER_ID, TODAY, null, null, null, "{not-json")));

            // Act
            DailyLogResponse result = dailyLogService.readLog(ELDER_ID, TODAY);

            // Assert
            assertThat(result.checklist()).isEmpty();
        }
    }

    @Nested
    @DisplayName("readMedicationIntakes")
    class ReadMedicationIntakes {

        @Test
        @DisplayName("복용 기록이 없는 약은 taken=null(미확인)로 반환한다")
        void should_return_null_taken_when_no_intake_record() {
            // Arrange
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01")));
            when(intakeRepository.findByElderIdAndIntakeDate(ELDER_ID, TODAY)).thenReturn(List.of());

            // Act
            List<MedicationIntakeResponse> result = dailyLogService.readMedicationIntakes(ELDER_ID, TODAY);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).taken()).isNull();
        }

        @Test
        @DisplayName("복용 기록이 있으면 해당 약의 taken 값을 채운다")
        void should_fill_taken_when_intake_record_exists() {
            // Arrange
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(
                            Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01"),
                            Fixtures.medication(8L, ELDER_ID, "메트포르민", "A10BA02")));
            when(intakeRepository.findByElderIdAndIntakeDate(ELDER_ID, TODAY))
                    .thenReturn(List.of(Fixtures.intake(1L, ELDER_ID, 7L, TODAY, true)));

            // Act
            List<MedicationIntakeResponse> result = dailyLogService.readMedicationIntakes(ELDER_ID, TODAY);

            // Assert
            assertThat(result).extracting(MedicationIntakeResponse::taken).containsExactly(true, null);
        }

        @Test
        @DisplayName("활성 약이 없으면 빈 목록을 반환한다")
        void should_return_empty_when_no_active_medication() {
            // Arrange
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of());
            when(intakeRepository.findByElderIdAndIntakeDate(ELDER_ID, TODAY)).thenReturn(List.of());

            // Act & Assert
            assertThat(dailyLogService.readMedicationIntakes(ELDER_ID, TODAY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("upsert (수동 입력)")
    class Upsert {

        @Test
        @DisplayName("로그가 없으면 새로 저장한다")
        void should_save_new_log_when_none_exists() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.upsert(USER_ID, ELDER_ID, new DailyLogUpdateRequest(
                    null, new BigDecimal("7.0"), 30, "좋음"));

            // Assert
            ElderDailyLog saved = captureSavedLog().getValue();
            assertThat(saved.getSleepHours()).isEqualByComparingTo("7.0");
            assertThat(saved.getExerciseMinutes()).isEqualTo(30);
            assertThat(saved.getConditionSummary()).isEqualTo("좋음");
        }

        @Test
        @DisplayName("logDate 를 생략하면 오늘 날짜로 저장한다")
        void should_use_today_when_logDate_is_omitted() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.upsert(USER_ID, ELDER_ID, new DailyLogUpdateRequest(null, null, 30, null));

            // Assert
            assertThat(captureSavedLog().getValue().getLogDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("logDate 를 지정하면 해당 날짜로 저장한다")
        void should_use_given_date_when_logDate_is_provided() {
            // Arrange
            LocalDate past = LocalDate.of(2026, 7, 1);
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, past)).thenReturn(Optional.empty());

            // Act
            dailyLogService.upsert(USER_ID, ELDER_ID, new DailyLogUpdateRequest(past, null, 30, null));

            // Assert
            assertThat(captureSavedLog().getValue().getLogDate()).isEqualTo(past);
        }

        @Test
        @DisplayName("기존 로그가 있으면 새로 저장하지 않고 갱신한다")
        void should_patch_existing_log_instead_of_saving() {
            // Arrange
            ElderDailyLog existing = Fixtures.dailyLog(1L, ELDER_ID, TODAY, new BigDecimal("6.0"), 20, "이전 요약", null);
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.of(existing));

            // Act
            dailyLogService.upsert(USER_ID, ELDER_ID, new DailyLogUpdateRequest(
                    null, new BigDecimal("8.0"), 45, "새 요약"));

            // Assert
            verify(dailyLogRepository, never()).save(any());
            assertThat(existing.getSleepHours()).isEqualByComparingTo("8.0");
            assertThat(existing.getExerciseMinutes()).isEqualTo(45);
            assertThat(existing.getConditionSummary()).isEqualTo("새 요약");
        }

        @Test
        @DisplayName("null 필드는 기존 값을 덮어쓰지 않는다 (부분 갱신)")
        void should_not_overwrite_existing_values_with_null() {
            // Arrange
            ElderDailyLog existing = Fixtures.dailyLog(1L, ELDER_ID, TODAY, new BigDecimal("6.0"), 20, "이전 요약", null);
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.of(existing));

            // Act: 수면만 갱신, 나머지는 null
            dailyLogService.upsert(USER_ID, ELDER_ID, new DailyLogUpdateRequest(
                    null, new BigDecimal("8.0"), null, null));

            // Assert
            assertThat(existing.getSleepHours()).isEqualByComparingTo("8.0");
            assertThat(existing.getExerciseMinutes()).isEqualTo(20);
            assertThat(existing.getConditionSummary()).isEqualTo("이전 요약");
        }

        @Test
        @DisplayName("소유권을 먼저 검증한다")
        void should_verify_ownership_before_writing() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.upsert(USER_ID, ELDER_ID, new DailyLogUpdateRequest(null, null, 10, null));

            // Assert
            verify(ownershipService).verify(USER_ID, ELDER_ID);
        }
    }

    @Nested
    @DisplayName("recordIntake")
    class RecordIntake {

        @Test
        @DisplayName("medicationId 가 없으면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_medicationId_is_null() {
            // Arrange
            MedicationIntakeRequest request = new MedicationIntakeRequest(null, true, null);

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.recordIntake(USER_ID, ELDER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("taken 이 없으면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_taken_is_null() {
            // Arrange
            MedicationIntakeRequest request = new MedicationIntakeRequest(7L, null, null);

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.recordIntake(USER_ID, ELDER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("약이 없으면 NOT_FOUND 이다")
        void should_throw_not_found_when_medication_does_not_exist() {
            // Arrange
            when(medicationRepository.findById(7L)).thenReturn(Optional.empty());
            MedicationIntakeRequest request = new MedicationIntakeRequest(7L, true, null);

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.recordIntake(USER_ID, ELDER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("다른 어르신의 약이면 FORBIDDEN 이다")
        void should_throw_forbidden_when_medication_belongs_to_another_elder() {
            // Arrange
            when(medicationRepository.findById(7L))
                    .thenReturn(Optional.of(Fixtures.medication(7L, 999L, "아모디핀", "C08CA01")));
            MedicationIntakeRequest request = new MedicationIntakeRequest(7L, true, null);

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.recordIntake(USER_ID, ELDER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("기록이 없으면 새 복약 기록을 저장한다")
        void should_save_new_intake_when_none_exists() {
            // Arrange
            when(medicationRepository.findById(7L))
                    .thenReturn(Optional.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01")));
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.empty());

            // Act
            MedicationIntakeResponse result = dailyLogService.recordIntake(
                    USER_ID, ELDER_ID, new MedicationIntakeRequest(7L, true, null));

            // Assert
            ElderMedicationIntake saved = captureSavedIntake().getValue();
            assertThat(saved.getMedicationId()).isEqualTo(7L);
            assertThat(saved.getTaken()).isTrue();
            assertThat(result.medicationName()).isEqualTo("아모디핀");
        }

        @Test
        @DisplayName("기록이 있으면 새로 저장하지 않고 갱신한다")
        void should_update_existing_intake_instead_of_saving() {
            // Arrange
            ElderMedicationIntake existing = Fixtures.intake(1L, ELDER_ID, 7L, TODAY, false);
            when(medicationRepository.findById(7L))
                    .thenReturn(Optional.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01")));
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.of(existing));

            // Act
            dailyLogService.recordIntake(USER_ID, ELDER_ID, new MedicationIntakeRequest(7L, true, null));

            // Assert
            verify(intakeRepository, never()).save(any());
            assertThat(existing.getTaken()).isTrue();
        }
    }

    @Nested
    @DisplayName("extractFromConversation - 대화 해석")
    class ExtractFromConversation {

        private void givenElder() {
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
        }

        private void givenConversation(Long convId) {
            when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                    Fixtures.conversation(convId, ELDER_ID, ConversationPurpose.free, "[]")));
        }

        private void givenLlmReply(String content) {
            when(openAiClient.chat(anyString(), anyList())).thenReturn(content);
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("LLM 이 반환한 수면·운동·요약을 로그로 저장한다")
        void should_persist_sleep_exercise_and_summary_from_llm() {
            // Arrange
            givenElder();
            givenConversation(50L);
            givenLlmReply("{\"sleepHours\": 6.5, \"exerciseMinutes\": 30, \"conditionSummary\": \"컨디션 좋음\"}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            ElderDailyLog saved = captureSavedLog().getValue();
            assertThat(saved.getSleepHours()).isEqualByComparingTo("6.5");
            assertThat(saved.getExerciseMinutes()).isEqualTo(30);
            assertThat(saved.getConditionSummary()).isEqualTo("컨디션 좋음");
            assertThat(saved.getSourceConversationId()).isEqualTo(50L);
        }

        @ParameterizedTest(name = "코드펜스 응답 파싱: {0}")
        @ValueSource(strings = {
                "```json\n{\"exerciseMinutes\": 30}\n```",
                "```\n{\"exerciseMinutes\": 30}\n```",
                "  ```json\n{\"exerciseMinutes\": 30}\n```  "
        })
        @DisplayName("모델이 코드펜스로 감싼 JSON 도 파싱한다")
        void should_strip_code_fence_before_parsing(String reply) {
            // Arrange
            givenElder();
            givenConversation(50L);
            givenLlmReply(reply);

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            assertThat(captureSavedLog().getValue().getExerciseMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("언급되지 않은 값(null)은 저장하지 않는다")
        void should_keep_nulls_when_llm_reports_null() {
            // Arrange
            givenElder();
            givenConversation(50L);
            givenLlmReply("{\"sleepHours\": null, \"exerciseMinutes\": null, \"conditionSummary\": null}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            ElderDailyLog saved = captureSavedLog().getValue();
            assertThat(saved.getSleepHours()).isNull();
            assertThat(saved.getExerciseMinutes()).isNull();
            assertThat(saved.getConditionSummary()).isNull();
        }

        @Test
        @DisplayName("빈 문자열 요약은 null 로 취급한다")
        void should_treat_blank_summary_as_null() {
            // Arrange
            givenElder();
            givenConversation(50L);
            givenLlmReply("{\"conditionSummary\": \"   \"}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            assertThat(captureSavedLog().getValue().getConditionSummary()).isNull();
        }

        @Test
        @DisplayName("숫자가 아닌 수면·운동 값은 null 로 무시한다")
        void should_ignore_non_numeric_sleep_and_exercise() {
            // Arrange: 모델이 문자열로 답한 경우
            givenElder();
            givenConversation(50L);
            givenLlmReply("{\"sleepHours\": \"여섯시간\", \"exerciseMinutes\": \"30분\"}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            ElderDailyLog saved = captureSavedLog().getValue();
            assertThat(saved.getSleepHours()).isNull();
            assertThat(saved.getExerciseMinutes()).isNull();
        }

        @Test
        @DisplayName("JSON 이 아니면 INTERNAL_ERROR 를 던진다")
        void should_throw_internal_error_when_llm_reply_is_not_json() {
            // Arrange
            givenElder();
            givenConversation(50L);
            when(openAiClient.chat(anyString(), anyList())).thenReturn("죄송해요, 잘 모르겠어요.");

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_ERROR);
        }

        @Test
        @DisplayName("conversationId 를 생략하면 가장 최근 대화를 사용한다")
        void should_use_latest_conversation_when_id_is_omitted() {
            // Arrange
            givenElder();
            when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of(
                    Fixtures.conversation(90L, ELDER_ID, ConversationPurpose.free, "[]"),
                    Fixtures.conversation(80L, ELDER_ID, ConversationPurpose.free, "[]")));
            givenLlmReply("{\"exerciseMinutes\": 10}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, null);

            // Assert
            assertThat(captureSavedLog().getValue().getSourceConversationId()).isEqualTo(90L);
        }

        @Test
        @DisplayName("분석할 대화가 하나도 없으면 NOT_FOUND 이다")
        void should_throw_not_found_when_no_conversation_exists() {
            // Arrange
            givenElder();
            when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of());

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.extractFromConversation(USER_ID, ELDER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("지정한 대화가 없으면 NOT_FOUND 이다")
        void should_throw_not_found_when_given_conversation_is_missing() {
            // Arrange
            givenElder();
            when(conversationRepository.findById(50L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("다른 어르신의 대화면 FORBIDDEN 이다")
        void should_throw_forbidden_when_conversation_belongs_to_another_elder() {
            // Arrange
            givenElder();
            when(conversationRepository.findById(50L)).thenReturn(Optional.of(
                    Fixtures.conversation(50L, 999L, ConversationPurpose.free, "[]")));

            // Act & Assert
            assertThatThrownBy(() -> dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("extractFromConversation - 약 이름 매칭")
    class MedicationNameMatching {

        private void arrange(String llmMedicationName) {
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
            when(conversationRepository.findById(50L)).thenReturn(Optional.of(
                    Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]")));
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀정", "C08CA01")));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());
            when(openAiClient.chat(anyString(), anyList())).thenReturn(
                    "{\"medicationsTaken\": [{\"medicationName\": \"" + llmMedicationName + "\", \"taken\": true}]}");
        }

        @ParameterizedTest(name = "\"{0}\" -> 아모디핀정 매칭")
        @ValueSource(strings = {
                "아모디핀정",   // 완전 일치
                "아모디핀",     // 부분 일치(등록명이 더 김)
                "아모디핀정 5mg", // 부분 일치(응답이 더 김)
                " 아모디핀정 "   // 공백 포함
        })
        @DisplayName("대소문자·부분 일치로 약을 찾아 복약 기록을 남긴다")
        void should_match_medication_by_partial_name(String name) {
            // Arrange
            arrange(name);
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            assertThat(captureSavedIntake().getValue().getMedicationId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("영문 약 이름은 대소문자를 무시하고 매칭한다")
        void should_match_medication_ignoring_case() {
            // Arrange
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
            when(conversationRepository.findById(50L)).thenReturn(Optional.of(
                    Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]")));
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "Aspirin", "B01AC06")));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());
            when(openAiClient.chat(anyString(), anyList())).thenReturn(
                    "{\"medicationsTaken\": [{\"medicationName\": \"ASPIRIN\", \"taken\": true}]}");
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            assertThat(captureSavedIntake().getValue().getTaken()).isTrue();
        }

        @Test
        @DisplayName("등록되지 않은 약 이름이면 기록하지 않는다")
        void should_not_record_when_medication_name_is_unknown() {
            // Arrange
            arrange("타이레놀");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            verify(intakeRepository, never()).save(any());
        }

        @Test
        @DisplayName("taken 이 없으면 해당 약을 건너뛴다")
        void should_skip_medication_when_taken_is_missing() {
            // Arrange
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
            when(conversationRepository.findById(50L)).thenReturn(Optional.of(
                    Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]")));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());
            when(openAiClient.chat(anyString(), anyList())).thenReturn(
                    "{\"medicationsTaken\": [{\"medicationName\": \"아모디핀정\", \"taken\": null}]}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            verify(intakeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("extractFromConversation - 질병 메모 갱신")
    class DiseaseUpdates {

        @Test
        @DisplayName("일치하는 질병의 notes 를 갱신한다")
        void should_update_disease_notes_when_name_matches() {
            // Arrange
            var disease = Fixtures.disease(3L, ELDER_ID, "고혈압", "I10", DiseaseStatus.active);
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
            when(conversationRepository.findById(50L)).thenReturn(Optional.of(
                    Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]")));
            when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active))
                    .thenReturn(List.of(disease));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());
            when(openAiClient.chat(anyString(), anyList())).thenReturn(
                    "{\"diseaseUpdates\": [{\"diseaseName\": \"고혈압\", \"note\": \"혈압 130/80 으로 안정적\"}]}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            assertThat(disease.getNotes()).isEqualTo("혈압 130/80 으로 안정적");
            assertThat(disease.getIcdCode()).isEqualTo("I10");
            assertThat(disease.getStatus()).isEqualTo(DiseaseStatus.active);
        }

        @Test
        @DisplayName("note 가 없으면 질병을 갱신하지 않는다")
        void should_skip_disease_when_note_is_missing() {
            // Arrange
            var disease = Fixtures.disease(3L, ELDER_ID, "고혈압", "I10", DiseaseStatus.active);
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
            when(conversationRepository.findById(50L)).thenReturn(Optional.of(
                    Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]")));
            when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active))
                    .thenReturn(List.of(disease));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());
            when(openAiClient.chat(anyString(), anyList())).thenReturn(
                    "{\"diseaseUpdates\": [{\"diseaseName\": \"고혈압\", \"note\": null}]}");

            // Act
            dailyLogService.extractFromConversation(USER_ID, ELDER_ID, 50L);

            // Assert
            assertThat(disease.getNotes()).isNull();
        }
    }

    @Nested
    @DisplayName("extractQuietly")
    class ExtractQuietly {

        @Test
        @DisplayName("LLM 호출이 실패해도 예외를 밖으로 던지지 않는다")
        void should_swallow_exception_when_llm_call_fails() {
            // Arrange
            Elder elder = Fixtures.elder(ELDER_ID, "김순자");
            AgentConversation conv = Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]");
            when(openAiClient.chat(anyString(), anyList()))
                    .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 통신 오류"));

            // Act & Assert
            assertThatCode(() -> dailyLogService.extractQuietly(elder, conv)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("LLM 응답이 JSON 이 아니어도 예외를 밖으로 던지지 않는다")
        void should_swallow_exception_when_reply_is_not_json() {
            // Arrange
            Elder elder = Fixtures.elder(ELDER_ID, "김순자");
            AgentConversation conv = Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]");
            when(openAiClient.chat(anyString(), anyList())).thenReturn("JSON 아님");

            // Act & Assert
            assertThatCode(() -> dailyLogService.extractQuietly(elder, conv)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("정상 응답이면 로그를 저장한다")
        void should_persist_log_when_extraction_succeeds() {
            // Arrange
            Elder elder = Fixtures.elder(ELDER_ID, "김순자");
            AgentConversation conv = Fixtures.conversation(50L, ELDER_ID, ConversationPurpose.free, "[]");
            when(openAiClient.chat(anyString(), anyList())).thenReturn("{\"sleepHours\": 7}");
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.extractQuietly(elder, conv);

            // Assert
            assertThat(captureSavedLog().getValue().getSleepHours()).isEqualByComparingTo("7");
        }
    }

    @Nested
    @DisplayName("applyCheckinAnswers")
    class ApplyCheckinAnswers {

        private ElderReminderResponse medicationReminder(String ruleCode, String medicationName) {
            return new ElderReminderResponse(ruleCode, RuleType.medication, "약 드셨어요?",
                    FrequencyType.daily, List.of("09:00"), ExpectedResponse.yes_no,
                    new ElderReminderResponse.MatchedBy("medication", "C08CA01", medicationName, null));
        }

        private ElderReminderResponse hydrationReminder(String ruleCode) {
            return new ElderReminderResponse(ruleCode, RuleType.hydration, "물 드세요",
                    FrequencyType.interval_hours, List.of("3"), ExpectedResponse.yes_no,
                    new ElderReminderResponse.MatchedBy("all", null, null, null));
        }

        @Test
        @DisplayName("체크리스트 응답을 JSON 으로 로그에 저장한다")
        void should_store_answers_as_checklist_json() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자", List.of(
                    new CheckinSubmitRequest.Answer("WATER", "yes"),
                    new CheckinSubmitRequest.Answer("MEAL", "no")), 60L);

            // Assert
            ElderDailyLog saved = captureSavedLog().getValue();
            assertThat(saved.getChecklistAnswers()).isEqualTo("{\"WATER\":\"yes\",\"MEAL\":\"no\"}");
            assertThat(saved.getSourceConversationId()).isEqualTo(60L);
        }

        @Test
        @DisplayName("ruleCode 나 answer 가 null 인 응답은 체크리스트에서 제외한다")
        void should_skip_answers_with_null_fields() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자", List.of(
                    new CheckinSubmitRequest.Answer("WATER", "yes"),
                    new CheckinSubmitRequest.Answer(null, "yes"),
                    new CheckinSubmitRequest.Answer("MEAL", null)), 60L);

            // Assert
            assertThat(captureSavedLog().getValue().getChecklistAnswers()).isEqualTo("{\"WATER\":\"yes\"}");
        }

        @Test
        @DisplayName("복약 규칙에 yes 로 답하면 해당 약을 복용함으로 기록한다")
        void should_record_medication_taken_when_answer_is_yes() {
            // Arrange
            when(reminderService.matchRules(ELDER_ID, "김순자"))
                    .thenReturn(List.of(medicationReminder("HTN_MED", "아모디핀정")));
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀정", "C08CA01")));
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.empty());
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자",
                    List.of(new CheckinSubmitRequest.Answer("HTN_MED", "yes")), 60L);

            // Assert
            ElderMedicationIntake saved = captureSavedIntake().getValue();
            assertThat(saved.getMedicationId()).isEqualTo(7L);
            assertThat(saved.getTaken()).isTrue();
            assertThat(saved.getSourceConversationId()).isEqualTo(60L);
        }

        @Test
        @DisplayName("복약 규칙에 no 로 답하면 복용하지 않음으로 기록한다")
        void should_record_medication_not_taken_when_answer_is_no() {
            // Arrange
            when(reminderService.matchRules(ELDER_ID, "김순자"))
                    .thenReturn(List.of(medicationReminder("HTN_MED", "아모디핀정")));
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀정", "C08CA01")));
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.empty());
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자",
                    List.of(new CheckinSubmitRequest.Answer("HTN_MED", "no")), 60L);

            // Assert
            assertThat(captureSavedIntake().getValue().getTaken()).isFalse();
        }

        @Test
        @DisplayName("복약 규칙이 아니면 복약 기록을 남기지 않는다")
        void should_not_record_intake_when_rule_is_not_medication() {
            // Arrange
            when(reminderService.matchRules(ELDER_ID, "김순자"))
                    .thenReturn(List.of(hydrationReminder("WATER")));
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀정", "C08CA01")));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자",
                    List.of(new CheckinSubmitRequest.Answer("WATER", "yes")), 60L);

            // Assert
            verify(intakeRepository, never()).save(any());
        }

        @Test
        @DisplayName("기존 복약 기록이 있으면 갱신한다")
        void should_update_existing_intake_when_record_exists() {
            // Arrange
            ElderMedicationIntake existing = Fixtures.intake(1L, ELDER_ID, 7L, TODAY, false);
            when(reminderService.matchRules(ELDER_ID, "김순자"))
                    .thenReturn(List.of(medicationReminder("HTN_MED", "아모디핀정")));
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀정", "C08CA01")));
            when(intakeRepository.findByMedicationIdAndIntakeDate(7L, TODAY)).thenReturn(Optional.of(existing));
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자",
                    List.of(new CheckinSubmitRequest.Answer("HTN_MED", "yes")), 60L);

            // Assert
            verify(intakeRepository, never()).save(any());
            assertThat(existing.getTaken()).isTrue();
        }

        @Test
        @DisplayName("체크인 반영은 수면·운동 값을 건드리지 않는다")
        void should_not_touch_sleep_and_exercise_when_applying_checkin() {
            // Arrange
            ElderDailyLog existing = Fixtures.dailyLog(1L, ELDER_ID, TODAY, new BigDecimal("6.0"), 20, "요약", null);
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.of(existing));

            // Act
            dailyLogService.applyCheckinAnswers(ELDER_ID, "김순자",
                    List.of(new CheckinSubmitRequest.Answer("WATER", "yes")), 60L);

            // Assert
            assertThat(existing.getSleepHours()).isEqualByComparingTo("6.0");
            assertThat(existing.getExerciseMinutes()).isEqualTo(20);
            assertThat(existing.getChecklistAnswers()).isEqualTo("{\"WATER\":\"yes\"}");
        }
    }

    @Nested
    @DisplayName("get / getIntakes")
    class OwnershipGuards {

        @Test
        @DisplayName("get 은 소유권을 검증한다")
        void should_verify_ownership_when_getting_log() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            dailyLogService.get(USER_ID, ELDER_ID, null);

            // Assert
            verify(ownershipService).verify(USER_ID, ELDER_ID);
        }

        @Test
        @DisplayName("getIntakes 는 소유권을 검증한다")
        void should_verify_ownership_when_getting_intakes() {
            // Arrange
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active))
                    .thenReturn(List.of());
            when(intakeRepository.findByElderIdAndIntakeDate(eq(ELDER_ID), any(LocalDate.class)))
                    .thenReturn(List.of());

            // Act
            dailyLogService.getIntakes(USER_ID, ELDER_ID, null);

            // Assert
            verify(ownershipService).verify(USER_ID, ELDER_ID);
        }

        @Test
        @DisplayName("날짜를 생략하면 오늘 로그를 조회한다")
        void should_use_today_when_date_is_omitted() {
            // Arrange
            when(dailyLogRepository.findByElderIdAndLogDate(ELDER_ID, TODAY)).thenReturn(Optional.empty());

            // Act
            DailyLogResponse result = dailyLogService.get(USER_ID, ELDER_ID, null);

            // Assert
            assertThat(result.logDate()).isEqualTo(TODAY);
        }
    }
}
