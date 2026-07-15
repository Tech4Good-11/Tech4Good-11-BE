package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.RuleType;
import com.example.demo.dto.checkin.CheckinSubmitRequest;
import com.example.demo.dto.checkin.CheckinSubmitResponse;
import com.example.demo.dto.checkin.CheckinTodayResponse;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CheckinService 단위 테스트. 오늘의 문진 항목 산출(yes_no 필터링)과 응답 제출 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckinService")
class CheckinServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ELDER_ID = 1L;

    @Mock
    private ReminderService reminderService;
    @Mock
    private OwnershipService ownershipService;
    @Mock
    private AgentConversationRepository conversationRepository;
    @Mock
    private DailyLogService dailyLogService;

    private CheckinService checkinService;

    @BeforeEach
    void setUp() {
        checkinService = new CheckinService(reminderService, ownershipService, conversationRepository,
                dailyLogService, new ObjectMapper());
    }

    private void givenElder() {
        when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID)).thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
    }

    private static ElderReminderResponse reminder(String ruleCode, ExpectedResponse expected) {
        return new ElderReminderResponse(ruleCode, RuleType.medication, ruleCode + " 메시지",
                FrequencyType.daily, List.of("09:00"), expected,
                new ElderReminderResponse.MatchedBy("all", null, null, null));
    }

    @Nested
    @DisplayName("today")
    class Today {

        @Test
        @DisplayName("yes_no 응답이 필요한 규칙만 문진 항목으로 반환한다")
        void should_return_only_yes_no_rules() {
            // Arrange
            givenElder();
            when(reminderService.matchRules(ELDER_ID, "김순자")).thenReturn(List.of(
                    reminder("MED_CHECK", ExpectedResponse.yes_no),
                    reminder("INFO_ONLY", ExpectedResponse.none),
                    reminder("WATER", ExpectedResponse.yes_no)));

            // Act
            List<CheckinTodayResponse> result = checkinService.today(USER_ID, ELDER_ID);

            // Assert
            assertThat(result).extracting(CheckinTodayResponse::ruleCode)
                    .containsExactly("MED_CHECK", "WATER");
        }

        @Test
        @DisplayName("규칙의 메시지와 시각을 그대로 싣는다")
        void should_carry_message_and_times_from_rule() {
            // Arrange
            givenElder();
            when(reminderService.matchRules(ELDER_ID, "김순자"))
                    .thenReturn(List.of(reminder("MED_CHECK", ExpectedResponse.yes_no)));

            // Act
            CheckinTodayResponse result = checkinService.today(USER_ID, ELDER_ID).get(0);

            // Assert
            assertThat(result.question()).isEqualTo("MED_CHECK 메시지");
            assertThat(result.scheduledTimes()).containsExactly("09:00");
            assertThat(result.expectedResponse()).isEqualTo(ExpectedResponse.yes_no);
        }

        @Test
        @DisplayName("yes_no 규칙이 없으면 빈 목록을 반환한다")
        void should_return_empty_when_no_yes_no_rule_matches() {
            // Arrange
            givenElder();
            when(reminderService.matchRules(ELDER_ID, "김순자"))
                    .thenReturn(List.of(reminder("INFO_ONLY", ExpectedResponse.none)));

            // Act & Assert
            assertThat(checkinService.today(USER_ID, ELDER_ID)).isEmpty();
        }

        @Test
        @DisplayName("소유권을 검증한다")
        void should_verify_ownership() {
            // Arrange
            givenElder();
            when(reminderService.matchRules(ELDER_ID, "김순자")).thenReturn(List.of());

            // Act
            checkinService.today(USER_ID, ELDER_ID);

            // Assert
            verify(ownershipService).verifyAndGetElder(USER_ID, ELDER_ID);
        }
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("answers 가 null 이면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_answers_is_null() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("answers 가 비어 있으면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_answers_is_empty() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(List.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("answers 가 비면 대화를 저장하지 않는다")
        void should_not_save_conversation_when_answers_is_empty() {
            // Arrange
            givenElder();

            // Act
            assertThatThrownBy(() -> checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(List.of())))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verify(conversationRepository, never()).save(any());
            verify(dailyLogService, never()).applyCheckinAnswers(anyLong(), anyString(), any(), anyLong());
        }

        @Test
        @DisplayName("응답을 daily_checkin 대화로 저장한다")
        void should_save_answers_as_daily_checkin_conversation() {
            // Arrange
            givenElder();
            when(conversationRepository.save(any(AgentConversation.class))).thenReturn(
                    Fixtures.conversation(80L, ELDER_ID, ConversationPurpose.daily_checkin, "[]"));

            // Act
            checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(List.of(
                    new CheckinSubmitRequest.Answer("MED_CHECK", "yes"))));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            AgentConversation saved = captor.getValue();
            assertThat(saved.getPurpose()).isEqualTo(ConversationPurpose.daily_checkin);
            assertThat(saved.getTranscript())
                    .isEqualTo("[{\"role\":\"elder\",\"ruleCode\":\"MED_CHECK\",\"answer\":\"yes\"}]");
        }

        @Test
        @DisplayName("저장된 대화 id 와 생성 시각을 반환한다")
        void should_return_conversation_id_and_created_at() {
            // Arrange
            givenElder();
            AgentConversation conv = Fixtures.conversation(80L, ELDER_ID, ConversationPurpose.daily_checkin, "[]");
            when(conversationRepository.save(any(AgentConversation.class))).thenReturn(conv);

            // Act
            CheckinSubmitResponse result = checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(
                    List.of(new CheckinSubmitRequest.Answer("MED_CHECK", "yes"))));

            // Assert
            assertThat(result.conversationId()).isEqualTo(80L);
            assertThat(result.savedAt()).isEqualTo(conv.getCreatedAt());
        }

        @Test
        @DisplayName("체크리스트·복약 반영을 대화 id 와 함께 위임한다")
        void should_delegate_to_daily_log_service_with_conversation_id() {
            // Arrange
            givenElder();
            when(conversationRepository.save(any(AgentConversation.class))).thenReturn(
                    Fixtures.conversation(80L, ELDER_ID, ConversationPurpose.daily_checkin, "[]"));
            List<CheckinSubmitRequest.Answer> answers = List.of(new CheckinSubmitRequest.Answer("MED_CHECK", "yes"));

            // Act
            checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(answers));

            // Assert
            verify(dailyLogService).applyCheckinAnswers(ELDER_ID, "김순자", answers, 80L);
        }

        @Test
        @DisplayName("여러 응답을 순서대로 transcript 에 담는다")
        void should_serialize_all_answers_in_order() {
            // Arrange
            givenElder();
            when(conversationRepository.save(any(AgentConversation.class))).thenReturn(
                    Fixtures.conversation(80L, ELDER_ID, ConversationPurpose.daily_checkin, "[]"));

            // Act
            checkinService.submit(USER_ID, ELDER_ID, new CheckinSubmitRequest(List.of(
                    new CheckinSubmitRequest.Answer("A", "yes"),
                    new CheckinSubmitRequest.Answer("B", "no"))));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getTranscript())
                    .isEqualTo("[{\"role\":\"elder\",\"ruleCode\":\"A\",\"answer\":\"yes\"},"
                            + "{\"role\":\"elder\",\"ruleCode\":\"B\",\"answer\":\"no\"}]");
        }
    }
}
