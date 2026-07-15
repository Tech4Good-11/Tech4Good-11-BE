package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.consult.ConsultRequest;
import com.example.demo.dto.consult.ConsultResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDailyLogRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ConsultService 단위 테스트.
 *
 * <p>핵심 계약: 자녀 상담은 (1) 어르신을 3인칭으로 지칭하고 (2) 어르신 기록을 근거로 답하며
 * (3) <b>저장되지도 지표로 추출되지도 않는다</b>.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultService")
class ConsultServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ELDER_ID = 1L;

    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private OwnershipService ownershipService;
    @Mock
    private ElderDiseaseRepository diseaseRepository;
    @Mock
    private ElderMedicationRepository medicationRepository;
    @Mock
    private ElderHealthNoteRepository healthNoteRepository;
    @Mock
    private AgentConversationRepository conversationRepository;
    @Mock
    private ElderDailyLogRepository dailyLogRepository;

    @InjectMocks
    private ConsultService consultService;

    private void givenElder() {
        when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                .thenReturn(Fixtures.elder(ELDER_ID, "박옥자"));
    }

    private void givenEmptyContext() {
        lenient().when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active)).thenReturn(List.of());
        lenient().when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active)).thenReturn(List.of());
        lenient().when(healthNoteRepository.findByElderId(ELDER_ID)).thenReturn(Optional.empty());
        lenient().when(dailyLogRepository.findTop7ByElderIdOrderByLogDateDesc(ELDER_ID)).thenReturn(List.of());
        lenient().when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of());
    }

    private void givenAiReply(String reply) {
        when(openAiClient.chat(anyString(), anyList())).thenReturn(reply);
    }

    private String captureSystemPrompt() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).chat(captor.capture(), anyList());
        return captor.getValue();
    }

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        @DisplayName("message 가 없으면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_message_is_null() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> consultService.consult(USER_ID, ELDER_ID, new ConsultRequest(null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("message 가 공백이면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_message_is_blank() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> consultService.consult(USER_ID, ELDER_ID, new ConsultRequest("   ", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("입력이 잘못되면 OpenAI 를 호출하지 않는다")
        void should_not_call_openai_when_message_is_invalid() {
            // Arrange
            givenElder();

            // Act
            assertThatThrownBy(() -> consultService.consult(USER_ID, ELDER_ID, new ConsultRequest(null, null)))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verifyNoInteractions(openAiClient);
        }
    }

    @Nested
    @DisplayName("어르신 대화와의 분리 (핵심 계약)")
    class SeparationFromElderChat {

        @Test
        @DisplayName("상담 내용을 대화 기록으로 저장하지 않는다")
        void should_not_save_conversation() {
            // Arrange
            givenElder();
            givenEmptyContext();
            givenAiReply("어머님께 여쭤보시는 게 좋겠어요.");

            // Act
            consultService.consult(USER_ID, ELDER_ID, new ConsultRequest("어머니 괜찮으신가요?", null));

            // Assert: 자녀 발화가 어르신 대화 이력을 오염시키면 안 된다
            verify(conversationRepository, org.mockito.Mockito.never())
                    .save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("응답에 conversationId 가 없다")
        void should_return_reply_without_conversation_id() {
            // Arrange
            givenElder();
            givenEmptyContext();
            givenAiReply("어머님께서는 최근 잘 지내고 계세요.");

            // Act
            ConsultResponse result = consultService.consult(USER_ID, ELDER_ID,
                    new ConsultRequest("어머니 요즘 어떠세요?", null));

            // Assert
            assertThat(result.reply()).isEqualTo("어머님께서는 최근 잘 지내고 계세요.");
        }
    }

    @Nested
    @DisplayName("시스템 프롬프트 구성")
    class SystemPrompt {

        @Test
        @DisplayName("대화 상대가 자녀임을 명시하고 어르신을 3인칭으로 지칭하게 한다")
        void should_instruct_ai_that_counterpart_is_guardian() {
            // Arrange
            givenElder();
            givenEmptyContext();
            givenAiReply("네");

            // Act
            consultService.consult(USER_ID, ELDER_ID, new ConsultRequest("질문", null));

            // Assert
            String prompt = captureSystemPrompt();
            assertThat(prompt).contains("자녀(보호자)");
            assertThat(prompt).contains("3인칭");
            assertThat(prompt).contains("박옥자님께서는");
        }

        @Test
        @DisplayName("어르신의 최근 대화 기록을 근거로 넣는다")
        void should_include_elder_recent_conversations_as_evidence() {
            // Arrange: 어르신이 "머리가 아파요" 라고 기록한 상황
            givenElder();
            lenient().when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active)).thenReturn(List.of());
            lenient().when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active)).thenReturn(List.of());
            lenient().when(healthNoteRepository.findByElderId(ELDER_ID)).thenReturn(Optional.empty());
            lenient().when(dailyLogRepository.findTop7ByElderIdOrderByLogDateDesc(ELDER_ID)).thenReturn(List.of());
            when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of(
                    Fixtures.conversation(1L, ELDER_ID, ConversationPurpose.free,
                            "[{\"role\":\"elder\",\"text\":\"머리가 아파요\"}]")));
            givenAiReply("네");

            // Act
            consultService.consult(USER_ID, ELDER_ID, new ConsultRequest("어머니 머리 아프다던데요?", null));

            // Assert: 자녀 질문에 답하려면 어르신이 실제로 한 말이 필요하다
            assertThat(captureSystemPrompt()).contains("머리가 아파요");
        }

        @Test
        @DisplayName("최근 생활 기록(수면·운동)을 근거로 넣는다")
        void should_include_recent_daily_logs_as_evidence() {
            // Arrange
            givenElder();
            lenient().when(diseaseRepository.findByElderIdAndStatus(ELDER_ID, DiseaseStatus.active)).thenReturn(List.of());
            lenient().when(medicationRepository.findByElderIdAndStatus(ELDER_ID, MedicationStatus.active)).thenReturn(List.of());
            lenient().when(healthNoteRepository.findByElderId(ELDER_ID)).thenReturn(Optional.empty());
            lenient().when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of());
            when(dailyLogRepository.findTop7ByElderIdOrderByLogDateDesc(ELDER_ID)).thenReturn(List.of(
                    Fixtures.dailyLog(1L, ELDER_ID, LocalDate.of(2026, 7, 16),
                            new BigDecimal("6.5"), 30, "산책 30분", null)));
            givenAiReply("네");

            // Act
            consultService.consult(USER_ID, ELDER_ID, new ConsultRequest("어머니 요즘 어떠세요?", null));

            // Assert
            String prompt = captureSystemPrompt();
            assertThat(prompt).contains("2026-07-16");
            assertThat(prompt).contains("6.5시간");
            assertThat(prompt).contains("30분");
        }

        @Test
        @DisplayName("기록이 없으면 지어내지 말라고 지시한다")
        void should_instruct_not_to_fabricate() {
            // Arrange
            givenElder();
            givenEmptyContext();
            givenAiReply("네");

            // Act
            consultService.consult(USER_ID, ELDER_ID, new ConsultRequest("질문", null));

            // Assert
            assertThat(captureSystemPrompt()).contains("지어내지");
        }
    }
}
