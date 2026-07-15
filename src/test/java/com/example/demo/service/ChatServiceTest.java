package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.chat.ChatRequest;
import com.example.demo.dto.chat.ChatResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 단위 테스트. 시스템 프롬프트 구성, 저장 조건, 지표 추출 위임을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService")
class ChatServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ELDER_ID = 1L;

    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private OwnershipService ownershipService;
    @Mock
    private DailyLogService dailyLogService;
    @Mock
    private ElderDiseaseRepository diseaseRepository;
    @Mock
    private ElderMedicationRepository medicationRepository;
    @Mock
    private ElderHealthNoteRepository healthNoteRepository;
    @Mock
    private AgentConversationRepository conversationRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(openAiClient, ownershipService, dailyLogService, diseaseRepository,
                medicationRepository, healthNoteRepository, conversationRepository, new ObjectMapper());
    }

    private void givenElder() {
        when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID)).thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
    }

    private void givenReply(String reply) {
        when(openAiClient.chat(anyString(), anyList())).thenReturn(reply);
    }

    private void givenConversationSaved(Long id) {
        when(conversationRepository.save(any(AgentConversation.class)))
                .thenReturn(Fixtures.conversation(id, ELDER_ID, ConversationPurpose.free, "[]"));
    }

    private String capturedSystemPrompt() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).chat(captor.capture(), anyList());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<OpenAiClient.ChatMessage> capturedMessages() {
        ArgumentCaptor<List<OpenAiClient.ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(openAiClient).chat(anyString(), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @ParameterizedTest(name = "message=\"{0}\"")
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("message 가 비어 있으면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_message_is_blank(String message) {
            // Arrange
            givenElder();
            ChatRequest request = new ChatRequest(message, null, null, false);

            // Act & Assert
            assertThatThrownBy(() -> chatService.chat(USER_ID, ELDER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("request 가 null 이면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_request_is_null() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> chatService.chat(USER_ID, ELDER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("message 가 비면 OpenAI 를 호출하지 않는다")
        void should_not_call_openai_when_message_is_blank() {
            // Arrange
            givenElder();

            // Act
            assertThatThrownBy(() -> chatService.chat(USER_ID, ELDER_ID, new ChatRequest("", null, null, false)))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verify(openAiClient, never()).chat(any(), any());
        }
    }

    @Nested
    @DisplayName("대화 저장")
    class Saving {

        @Test
        @DisplayName("save 가 false 면 저장하지 않고 conversationId 는 null 이다")
        void should_not_save_when_save_is_false() {
            // Arrange
            givenElder();
            givenReply("안녕하세요, 어르신.");

            // Act
            ChatResponse result = chatService.chat(USER_ID, ELDER_ID,
                    new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(result.reply()).isEqualTo("안녕하세요, 어르신.");
            assertThat(result.conversationId()).isNull();
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("save 가 null 이면 저장하지 않는다 (기본값 false)")
        void should_not_save_when_save_is_null() {
            // Arrange
            givenElder();
            givenReply("안녕하세요.");

            // Act
            ChatResponse result = chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, null));

            // Assert
            assertThat(result.conversationId()).isNull();
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("save 가 true 면 저장하고 conversationId 를 반환한다")
        void should_save_and_return_conversation_id_when_save_is_true() {
            // Arrange
            givenElder();
            givenReply("안녕하세요.");
            givenConversationSaved(90L);

            // Act
            ChatResponse result = chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, true));

            // Assert
            assertThat(result.conversationId()).isEqualTo(90L);
            verify(conversationRepository).save(any(AgentConversation.class));
        }

        @Test
        @DisplayName("purpose 를 생략하면 free 로 저장한다")
        void should_save_with_free_purpose_when_purpose_is_omitted() {
            // Arrange
            givenElder();
            givenReply("안녕하세요.");
            givenConversationSaved(90L);

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, true));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getPurpose()).isEqualTo(ConversationPurpose.free);
        }

        @Test
        @DisplayName("purpose 를 지정하면 그대로 저장한다")
        void should_save_with_given_purpose() {
            // Arrange
            givenElder();
            givenReply("안녕하세요.");
            givenConversationSaved(90L);

            // Act
            chatService.chat(USER_ID, ELDER_ID,
                    new ChatRequest("안녕", null, ConversationPurpose.daily_checkin, true));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getPurpose()).isEqualTo(ConversationPurpose.daily_checkin);
        }

        @Test
        @DisplayName("transcript 에 어르신 발화와 에이전트 답변을 순서대로 담는다")
        void should_serialize_turns_into_transcript() {
            // Arrange
            givenElder();
            givenReply("네, 잘 하셨어요.");
            givenConversationSaved(90L);

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("약 먹었어요", null, null, true));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getTranscript()).isEqualTo(
                    "[{\"role\":\"elder\",\"text\":\"약 먹었어요\"},{\"role\":\"agent\",\"text\":\"네, 잘 하셨어요.\"}]");
        }

        @Test
        @DisplayName("history 의 assistant 는 agent, user 는 elder 로 변환해 저장한다")
        void should_map_history_roles_when_saving() {
            // Arrange
            givenElder();
            givenReply("네.");
            givenConversationSaved(90L);
            List<ChatRequest.Turn> history = List.of(
                    new ChatRequest.Turn("user", "안녕"),
                    new ChatRequest.Turn("assistant", "안녕하세요"));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("잘 잤어요", history, null, true));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getTranscript()).isEqualTo(
                    "[{\"role\":\"elder\",\"text\":\"안녕\"},{\"role\":\"agent\",\"text\":\"안녕하세요\"},"
                            + "{\"role\":\"elder\",\"text\":\"잘 잤어요\"},{\"role\":\"agent\",\"text\":\"네.\"}]");
        }

        @Test
        @DisplayName("내용이 빈 history 턴은 저장에서 제외한다")
        void should_skip_blank_history_turns_when_saving() {
            // Arrange
            givenElder();
            givenReply("네.");
            givenConversationSaved(90L);
            List<ChatRequest.Turn> history = List.of(
                    new ChatRequest.Turn("user", "  "),
                    new ChatRequest.Turn("assistant", null));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", history, null, true));

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getTranscript()).isEqualTo(
                    "[{\"role\":\"elder\",\"text\":\"안녕\"},{\"role\":\"agent\",\"text\":\"네.\"}]");
        }
    }

    @Nested
    @DisplayName("지표 추출 위임")
    class ExtractDelegation {

        @Test
        @DisplayName("save 가 true 면 저장된 대화로 지표 추출을 위임한다")
        void should_delegate_extraction_when_save_is_true() {
            // Arrange
            givenElder();
            givenReply("네.");
            givenConversationSaved(90L);

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, true));

            // Assert
            verify(dailyLogService).extractQuietly(any(), any(AgentConversation.class));
        }

        @Test
        @DisplayName("save 가 false 면 지표 추출을 하지 않는다")
        void should_not_delegate_extraction_when_save_is_false() {
            // Arrange
            givenElder();
            givenReply("네.");

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            verify(dailyLogService, never()).extractQuietly(any(), any());
        }
    }

    @Nested
    @DisplayName("시스템 프롬프트")
    class SystemPrompt {

        @Test
        @DisplayName("어르신 이름을 프롬프트에 포함한다")
        void should_include_elder_name() {
            // Arrange
            givenElder();
            givenReply("네.");

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedSystemPrompt()).contains("- 이름: 김순자");
        }

        @Test
        @DisplayName("활성 질환을 프롬프트에 포함한다")
        void should_include_active_diseases() {
            // Arrange
            givenElder();
            givenReply("네.");
            when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active)).thenReturn(List.of(
                    Fixtures.disease(1L, ELDER_ID, "고혈압", "I10", DiseaseStatus.active),
                    Fixtures.disease(2L, ELDER_ID, "당뇨", "E11", DiseaseStatus.active)));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedSystemPrompt()).contains("- 현재 질환: 고혈압, 당뇨");
        }

        @Test
        @DisplayName("복용 약을 용량과 함께 프롬프트에 포함한다")
        void should_include_medications_with_dosage() {
            // Arrange
            givenElder();
            givenReply("네.");
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active)).thenReturn(
                    List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01", "1정", MedicationStatus.active)));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedSystemPrompt()).contains("- 복용 약: 아모디핀(1정)");
        }

        @Test
        @DisplayName("용량이 없는 약은 괄호 없이 표기한다")
        void should_omit_parentheses_when_dosage_is_absent() {
            // Arrange
            givenElder();
            givenReply("네.");
            when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active)).thenReturn(
                    List.of(Fixtures.medication(7L, ELDER_ID, "아모디핀", "C08CA01", null, MedicationStatus.active)));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedSystemPrompt()).contains("- 복용 약: 아모디핀\n");
        }

        @Test
        @DisplayName("건강 노트가 있으면 프롬프트에 포함한다")
        void should_include_health_note_when_present() {
            // Arrange
            givenElder();
            givenReply("네.");
            when(healthNoteRepository.findByElderId(ELDER_ID))
                    .thenReturn(Optional.of(Fixtures.healthNote(1L, ELDER_ID, "## 최근 상태\n- 컨디션 양호")));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedSystemPrompt()).contains("[건강 노트]").contains("컨디션 양호");
        }

        @Test
        @DisplayName("질환·약·노트가 없으면 해당 항목을 넣지 않는다")
        void should_omit_sections_when_no_context_exists() {
            // Arrange
            givenElder();
            givenReply("네.");

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedSystemPrompt())
                    .doesNotContain("- 현재 질환:")
                    .doesNotContain("- 복용 약:")
                    .doesNotContain("[건강 노트]");
        }
    }

    @Nested
    @DisplayName("메시지 구성")
    class MessageComposition {

        @Test
        @DisplayName("history 뒤에 이번 발화를 user 로 덧붙인다")
        void should_append_current_message_after_history() {
            // Arrange
            givenElder();
            givenReply("네.");
            List<ChatRequest.Turn> history = List.of(
                    new ChatRequest.Turn("user", "안녕"),
                    new ChatRequest.Turn("assistant", "안녕하세요"));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("잘 잤어요", history, null, false));

            // Assert
            assertThat(capturedMessages()).containsExactly(
                    new OpenAiClient.ChatMessage("user", "안녕"),
                    new OpenAiClient.ChatMessage("assistant", "안녕하세요"),
                    new OpenAiClient.ChatMessage("user", "잘 잤어요"));
        }

        @Test
        @DisplayName("assistant 가 아닌 role 은 모두 user 로 정규화한다")
        void should_normalize_unknown_roles_to_user() {
            // Arrange
            givenElder();
            givenReply("네.");
            List<ChatRequest.Turn> history = List.of(new ChatRequest.Turn("system", "무시될 역할"));

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", history, null, false));

            // Assert
            assertThat(capturedMessages()).first()
                    .isEqualTo(new OpenAiClient.ChatMessage("user", "무시될 역할"));
        }

        @Test
        @DisplayName("history 가 없으면 이번 발화만 보낸다")
        void should_send_only_current_message_when_history_is_absent() {
            // Arrange
            givenElder();
            givenReply("네.");

            // Act
            chatService.chat(USER_ID, ELDER_ID, new ChatRequest("안녕", null, null, false));

            // Assert
            assertThat(capturedMessages()).containsExactly(new OpenAiClient.ChatMessage("user", "안녕"));
        }
    }
}
