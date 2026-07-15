package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderHealthNote;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.document.DocumentIntakeResponse;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentService 단위 테스트. Vision 호출(OpenAiClient)만 모킹하고
 * JSON 파싱·엔티티 저장 로직을 실제 ObjectMapper 로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService")
class DocumentServiceTest {

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

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                openAiClient, ownershipService, diseaseRepository, medicationRepository,
                healthNoteRepository, conversationRepository, new ObjectMapper());
    }

    private static MultipartFile image() {
        return new MockMultipartFile("file", "처방전.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private void givenElder() {
        when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID)).thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
    }

    private void givenVisionReply(String json) {
        when(openAiClient.extractFromImage(any(), anyString(), anyString(), anyString(), any())).thenReturn(json);
    }

    private void givenConversationSaved() {
        when(conversationRepository.save(any(AgentConversation.class))).thenReturn(
                Fixtures.conversation(70L, ELDER_ID, ConversationPurpose.document_intake, "[]"));
    }

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        @DisplayName("파일이 null 이면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_file_is_null() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, null, "prescription"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("파일이 비어 있으면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_file_is_empty() {
            // Arrange
            givenElder();
            MultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

            // Act & Assert
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, empty, "prescription"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @ParameterizedTest(name = "docType={0}")
        @ValueSource(strings = {"invalid", "DIAGNOSIS", "처방전", ""})
        @DisplayName("docType 이 diagnosis/prescription 이 아니면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_docType_is_not_allowed(String docType) {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, image(), docType))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("docType 이 null 이면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_docType_is_null() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, image(), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("입력이 잘못되면 Vision 을 호출하지 않는다")
        void should_not_call_vision_when_input_is_invalid() {
            // Arrange
            givenElder();

            // Act
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, image(), "invalid"))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verify(openAiClient, never()).extractFromImage(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Vision 응답 파싱과 저장")
    class ParsingAndPersistence {

        @Test
        @DisplayName("추출된 약을 활성 상태로 저장한다")
        void should_save_extracted_medications_as_active() {
            // Arrange
            givenElder();
            givenVisionReply("""
                    {"rawText":"처방전","medications":[
                      {"medicationName":"아모디핀정","atcCode":"C08CA01","dosage":"1정/5mg","intervalHours":24,
                       "startDate":"2026-07-01","endDate":null}],
                     "diseases":[]}
                    """);
            when(medicationRepository.save(any(ElderMedication.class)))
                    .thenAnswer(i -> i.getArgument(0));
            givenConversationSaved();

            // Act
            DocumentIntakeResponse result = documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            ArgumentCaptor<ElderMedication> captor = ArgumentCaptor.forClass(ElderMedication.class);
            verify(medicationRepository).save(captor.capture());
            ElderMedication saved = captor.getValue();
            assertThat(saved.getMedicationName()).isEqualTo("아모디핀정");
            assertThat(saved.getAtcCode()).isEqualTo("C08CA01");
            assertThat(saved.getIntervalHours()).isEqualTo(24);
            assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(saved.getStatus()).isEqualTo(MedicationStatus.active);
            assertThat(result.extractedMedications()).hasSize(1);
        }

        @Test
        @DisplayName("추출된 질병을 활성 상태로 저장한다")
        void should_save_extracted_diseases_as_active() {
            // Arrange
            givenElder();
            givenVisionReply("""
                    {"rawText":"진단서","medications":[],
                     "diseases":[{"diseaseName":"고혈압","icdCode":"I10","diagnosedAt":"2020-05-11","notes":"경증"}]}
                    """);
            when(diseaseRepository.save(any(ElderDisease.class))).thenAnswer(i -> i.getArgument(0));
            givenConversationSaved();

            // Act
            DocumentIntakeResponse result = documentService.process(USER_ID, ELDER_ID, image(), "diagnosis");

            // Assert
            ArgumentCaptor<ElderDisease> captor = ArgumentCaptor.forClass(ElderDisease.class);
            verify(diseaseRepository).save(captor.capture());
            ElderDisease saved = captor.getValue();
            assertThat(saved.getDiseaseName()).isEqualTo("고혈압");
            assertThat(saved.getIcdCode()).isEqualTo("I10");
            assertThat(saved.getDiagnosedAt()).isEqualTo(LocalDate.of(2020, 5, 11));
            assertThat(saved.getStatus()).isEqualTo(DiseaseStatus.active);
            assertThat(result.extractedDiseases()).hasSize(1);
        }

        @Test
        @DisplayName("응답 형태를 강제하는 strict 스키마로 호출한다")
        void should_call_vision_with_strict_schema() {
            // Arrange
            givenElder();
            givenVisionReply("{\"medications\":[],\"diseases\":[]}");
            givenConversationSaved();
            ArgumentCaptor<ObjectNode> schemaCaptor = ArgumentCaptor.forClass(ObjectNode.class);

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert: 프롬프트에 형식을 부탁하는 대신 스키마로 강제해야 한다
            verify(openAiClient).extractFromImage(any(), any(), anyString(),
                    eq("document_extraction"), schemaCaptor.capture());
            ObjectNode schema = schemaCaptor.getValue();
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            assertThat(schema.path("properties").path("medications").path("items")
                    .path("properties").path("intervalHours").path("type").toString())
                    .isEqualTo("[\"integer\",\"null\"]");
        }

        @Test
        @DisplayName("이름이 없는 약은 건너뛴다")
        void should_skip_medication_without_name() {
            // Arrange
            givenElder();
            givenVisionReply("{\"medications\":[{\"medicationName\":null},{\"medicationName\":\"  \"}],\"diseases\":[]}");
            givenConversationSaved();

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            verify(medicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("잘못된 날짜 문자열은 null 로 저장한다")
        void should_store_null_when_date_is_unparseable() {
            // Arrange
            givenElder();
            givenVisionReply("{\"medications\":[{\"medicationName\":\"아스피린\",\"startDate\":\"2026년 7월\"}],\"diseases\":[]}");
            when(medicationRepository.save(any(ElderMedication.class))).thenAnswer(i -> i.getArgument(0));
            givenConversationSaved();

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            ArgumentCaptor<ElderMedication> captor = ArgumentCaptor.forClass(ElderMedication.class);
            verify(medicationRepository).save(captor.capture());
            assertThat(captor.getValue().getStartDate()).isNull();
        }

        @Test
        @DisplayName("intervalHours 를 정수로 저장한다")
        void should_store_interval_hours_as_integer() {
            // Arrange: 스키마가 integer 를 강제하므로 모델은 숫자로만 답한다
            givenElder();
            givenVisionReply("{\"medications\":[{\"medicationName\":\"아스피린\",\"intervalHours\":12}],\"diseases\":[]}");
            when(medicationRepository.save(any(ElderMedication.class))).thenAnswer(i -> i.getArgument(0));
            givenConversationSaved();

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            ArgumentCaptor<ElderMedication> captor = ArgumentCaptor.forClass(ElderMedication.class);
            verify(medicationRepository).save(captor.capture());
            assertThat(captor.getValue().getIntervalHours()).isEqualTo(12);
        }

        @Test
        @DisplayName("숫자가 아닌 intervalHours 는 null 로 저장한다")
        void should_store_null_when_interval_hours_is_not_numeric() {
            // Arrange
            givenElder();
            givenVisionReply("{\"medications\":[{\"medicationName\":\"아스피린\",\"intervalHours\":\"하루 한 번\"}],\"diseases\":[]}");
            when(medicationRepository.save(any(ElderMedication.class))).thenAnswer(i -> i.getArgument(0));
            givenConversationSaved();

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            ArgumentCaptor<ElderMedication> captor = ArgumentCaptor.forClass(ElderMedication.class);
            verify(medicationRepository).save(captor.capture());
            assertThat(captor.getValue().getIntervalHours()).isNull();
        }

        @Test
        @DisplayName("판독 결과가 JSON 이 아니면 재촬영 안내와 함께 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_vision_reply_is_not_json() {
            // Arrange
            givenElder();
            givenVisionReply("이미지가 흐릿해서 판독할 수 없습니다.");

            // Act & Assert
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, image(), "prescription"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                        assertThat(e).hasMessageContaining("더 선명한 이미지");
                    });
        }

        @Test
        @DisplayName("판독 실패 시 아무것도 저장하지 않는다")
        void should_not_persist_anything_when_parsing_fails() {
            // Arrange
            givenElder();
            givenVisionReply("판독 불가");

            // Act
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, image(), "prescription"))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verify(medicationRepository, never()).save(any());
            verify(diseaseRepository, never()).save(any());
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("처리 과정을 document_intake 대화로 기록한다")
        void should_record_process_as_document_intake_conversation() {
            // Arrange
            givenElder();
            givenVisionReply("{\"rawText\":\"처방전 원문\",\"medications\":[],\"diseases\":[]}");
            givenConversationSaved();

            // Act
            DocumentIntakeResponse result = documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
            verify(conversationRepository).save(captor.capture());
            AgentConversation saved = captor.getValue();
            assertThat(saved.getPurpose()).isEqualTo(ConversationPurpose.document_intake);
            assertThat(saved.getTranscript()).contains("처방전 원문").contains("처방전.jpg");
            assertThat(result.conversationId()).isEqualTo(70L);
        }
    }

    @Nested
    @DisplayName("건강 노트 반영")
    class HealthNoteAppend {

        @Test
        @DisplayName("추출 결과가 없으면 노트를 갱신하지 않는다")
        void should_not_update_note_when_nothing_extracted() {
            // Arrange
            givenElder();
            givenVisionReply("{\"medications\":[],\"diseases\":[]}");
            givenConversationSaved();

            // Act
            DocumentIntakeResponse result = documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            assertThat(result.healthNoteUpdated()).isFalse();
            verify(healthNoteRepository, never()).save(any());
        }

        @Test
        @DisplayName("노트가 없으면 새로 만든다")
        void should_create_note_when_absent() {
            // Arrange
            givenElder();
            givenVisionReply("{\"medications\":[{\"medicationName\":\"아스피린\"}],\"diseases\":[]}");
            when(medicationRepository.save(any(ElderMedication.class))).thenAnswer(i -> i.getArgument(0));
            when(healthNoteRepository.findByElderId(ELDER_ID)).thenReturn(Optional.empty());
            givenConversationSaved();

            // Act
            DocumentIntakeResponse result = documentService.process(USER_ID, ELDER_ID, image(), "prescription");

            // Assert
            ArgumentCaptor<ElderHealthNote> captor = ArgumentCaptor.forClass(ElderHealthNote.class);
            verify(healthNoteRepository).save(captor.capture());
            assertThat(captor.getValue().getContentMd()).contains("처방전 반영: 아스피린");
            assertThat(result.healthNoteUpdated()).isTrue();
        }

        @Test
        @DisplayName("노트가 있으면 기존 내용 뒤에 덧붙인다")
        void should_append_to_existing_note() {
            // Arrange
            ElderHealthNote existing = Fixtures.healthNote(1L, ELDER_ID, "## 최근 상태");
            givenElder();
            givenVisionReply("{\"medications\":[],\"diseases\":[{\"diseaseName\":\"고혈압\"}]}");
            when(diseaseRepository.save(any(ElderDisease.class))).thenAnswer(i -> i.getArgument(0));
            when(healthNoteRepository.findByElderId(ELDER_ID)).thenReturn(Optional.of(existing));
            givenConversationSaved();

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "diagnosis");

            // Assert
            verify(healthNoteRepository, never()).save(any());
            assertThat(existing.getContentMd()).isEqualTo("## 최근 상태\n- (자동) 진단서 반영: 고혈압");
        }

        @Test
        @DisplayName("질병과 약을 함께 한 줄로 요약한다")
        void should_summarize_diseases_and_medications_together() {
            // Arrange
            givenElder();
            givenVisionReply("""
                    {"medications":[{"medicationName":"아스피린"}],
                     "diseases":[{"diseaseName":"고혈압"}]}
                    """);
            when(medicationRepository.save(any(ElderMedication.class))).thenAnswer(i -> i.getArgument(0));
            when(diseaseRepository.save(any(ElderDisease.class))).thenAnswer(i -> i.getArgument(0));
            when(healthNoteRepository.findByElderId(ELDER_ID)).thenReturn(Optional.empty());
            givenConversationSaved();

            // Act
            documentService.process(USER_ID, ELDER_ID, image(), "diagnosis");

            // Assert
            ArgumentCaptor<ElderHealthNote> captor = ArgumentCaptor.forClass(ElderHealthNote.class);
            verify(healthNoteRepository).save(captor.capture());
            assertThat(captor.getValue().getContentMd()).contains("진단서 반영: 고혈압, 아스피린");
        }
    }

    @Nested
    @DisplayName("소유권")
    class Ownership {

        @Test
        @DisplayName("소유권 검증 실패 시 파일을 읽지 않는다")
        void should_not_read_file_when_ownership_fails() {
            // Arrange
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

            // Act & Assert
            assertThatThrownBy(() -> documentService.process(USER_ID, ELDER_ID, image(), "prescription"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
            verify(openAiClient, never()).extractFromImage(any(), any(), any(), any(), any());
        }
    }
}
