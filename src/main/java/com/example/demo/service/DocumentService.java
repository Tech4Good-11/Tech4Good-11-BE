package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.common.JsonSchemas;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderHealthNote;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.dto.document.DocumentIntakeResponse;
import com.example.demo.dto.medication.MedicationResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 진단서/처방전 문서 처리.
 * 업로드된 이미지를 OpenAI Vision 으로 판독하여 질병/복약을 구조화 추출한 뒤
 * elder_disease / elder_medication 실데이터로 저장하고, 처리 과정을 agent_conversation 에 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final OpenAiClient openAiClient;
    private final OwnershipService ownershipService;
    private final ElderDiseaseRepository diseaseRepository;
    private final ElderMedicationRepository medicationRepository;
    private final ElderHealthNoteRepository healthNoteRepository;
    private final AgentConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DocumentIntakeResponse process(Long userId, Long elderId, MultipartFile file, String docType) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 없습니다.");
        }
        if (docType == null || (!docType.equals("diagnosis") && !docType.equals("prescription"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "docType 은 diagnosis 또는 prescription 이어야 합니다.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일을 읽을 수 없습니다.");
        }

        // 1) Vision 으로 문서 판독 → 스키마가 강제된 JSON
        String docWord = docType.equals("prescription") ? "처방전" : "진단서";
        String instruction = buildInstruction(docWord);
        String content = openAiClient.extractFromImage(bytes, file.getContentType(), instruction,
                "document_extraction", extractionSchema());
        JsonNode extracted = parseJson(content);

        // 2) 추출 결과를 실데이터로 저장
        List<MedicationResponse> extractedMedications = saveMedications(elderId, extracted.path("medications"));
        List<DiseaseResponse> extractedDiseases = saveDiseases(elderId, extracted.path("diseases"));

        // 3) 처리 과정을 document_intake 대화로 기록(원문 포함)
        AgentConversation conv = conversationRepository.save(AgentConversation.builder()
                .elderId(elderId)
                .purpose(ConversationPurpose.document_intake)
                .transcript(buildTranscript(docWord, file.getOriginalFilename(), extracted))
                .build());

        // 4) 건강 노트에 반영 요약 추가
        boolean noteUpdated = appendHealthNote(elderId, docWord, extractedMedications, extractedDiseases);

        return new DocumentIntakeResponse(
                conv.getId(),
                docType,
                extractedMedications,
                extractedDiseases,
                noteUpdated
        );
    }

    /** 출력 형태는 extractionSchema() 가 강제하므로 '무엇을' 뽑을지만 지시한다. */
    private String buildInstruction(String docWord) {
        return "첨부된 이미지는 한국의 의료 " + docWord + " 사진이다. 내용을 판독해 처방된 약과 진단된 질병을 추출하라.\n"
                + "규칙:\n"
                + "- rawText 에는 이미지에서 읽은 핵심 원문 텍스트를 담아라.\n"
                + "- 날짜는 \"YYYY-MM-DD\" 형식. intervalHours 는 복용 간격(시간 단위, 예: 24=1일1회).\n"
                + "- 이미지에서 알 수 없는 값은 null, 해당 항목이 없으면 빈 배열([]). 추측하지 마라.";
    }

    /**
     * 문서 판독 응답 스키마(Structured Outputs, strict).
     * strict 규칙: 모든 프로퍼티가 required, additionalProperties=false, 선택값은 nullable.
     * (날짜는 JSON 에 날짜 타입이 없어 문자열로 받고 dateOrNull 로 해석한다)
     */
    private ObjectNode extractionSchema() {
        ObjectNode schema = JsonSchemas.object(objectMapper);
        schema.putArray("required").add("rawText").add("medications").add("diseases");

        ObjectNode props = schema.putObject("properties");
        props.set("rawText", JsonSchemas.nullable(objectMapper, "string"));

        props.set("medications", JsonSchemas.arrayOf(objectMapper,
                List.of("medicationName", "atcCode", "dosage", "intervalHours", "startDate", "endDate"),
                Map.of("medicationName", JsonSchemas.of(objectMapper, "string"),
                        "atcCode", JsonSchemas.nullable(objectMapper, "string"),
                        "dosage", JsonSchemas.nullable(objectMapper, "string"),
                        "intervalHours", JsonSchemas.nullable(objectMapper, "integer"),
                        "startDate", JsonSchemas.nullable(objectMapper, "string"),
                        "endDate", JsonSchemas.nullable(objectMapper, "string"))));

        props.set("diseases", JsonSchemas.arrayOf(objectMapper,
                List.of("diseaseName", "icdCode", "diagnosedAt", "notes"),
                Map.of("diseaseName", JsonSchemas.of(objectMapper, "string"),
                        "icdCode", JsonSchemas.nullable(objectMapper, "string"),
                        "diagnosedAt", JsonSchemas.nullable(objectMapper, "string"),
                        "notes", JsonSchemas.nullable(objectMapper, "string"))));

        return schema;
    }

    private List<MedicationResponse> saveMedications(Long elderId, JsonNode medications) {
        List<MedicationResponse> result = new ArrayList<>();
        if (medications == null || !medications.isArray()) {
            return result;
        }
        for (JsonNode m : medications) {
            String name = text(m.path("medicationName"));
            if (name == null || name.isBlank()) {
                continue;
            }
            ElderMedication saved = medicationRepository.save(ElderMedication.builder()
                    .elderId(elderId)
                    .medicationName(name)
                    .atcCode(text(m.path("atcCode")))
                    .dosage(text(m.path("dosage")))
                    .intervalHours(intOrNull(m.path("intervalHours")))
                    .startDate(dateOrNull(m.path("startDate")))
                    .endDate(dateOrNull(m.path("endDate")))
                    .status(MedicationStatus.active)
                    .build());
            result.add(MedicationResponse.from(saved));
        }
        return result;
    }

    private List<DiseaseResponse> saveDiseases(Long elderId, JsonNode diseases) {
        List<DiseaseResponse> result = new ArrayList<>();
        if (diseases == null || !diseases.isArray()) {
            return result;
        }
        for (JsonNode d : diseases) {
            String name = text(d.path("diseaseName"));
            if (name == null || name.isBlank()) {
                continue;
            }
            ElderDisease saved = diseaseRepository.save(ElderDisease.builder()
                    .elderId(elderId)
                    .diseaseName(name)
                    .icdCode(text(d.path("icdCode")))
                    .diagnosedAt(dateOrNull(d.path("diagnosedAt")))
                    .status(DiseaseStatus.active)
                    .notes(text(d.path("notes")))
                    .build());
            result.add(DiseaseResponse.from(saved));
        }
        return result;
    }

    private String buildTranscript(String docWord, String filename, JsonNode extracted) {
        ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode agent = arr.addObject();
        agent.put("role", "agent");
        agent.put("text", docWord + " 문서(" + (filename == null ? "unknown" : filename) + ")를 판독했습니다.");
        ObjectNode result = arr.addObject();
        result.put("role", "system");
        result.put("text", text(extracted.path("rawText")));
        result.set("extracted", extracted);
        try {
            return objectMapper.writeValueAsString(arr);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "대화 기록 직렬화 실패");
        }
    }

    private boolean appendHealthNote(Long elderId, String docWord,
                                     List<MedicationResponse> meds, List<DiseaseResponse> diseases) {
        if (meds.isEmpty() && diseases.isEmpty()) {
            return false;
        }
        StringBuilder line = new StringBuilder("\n- (자동) ").append(docWord).append(" 반영: ");
        List<String> items = new ArrayList<>();
        diseases.forEach(d -> items.add(d.diseaseName()));
        meds.forEach(m -> items.add(m.medicationName()));
        line.append(String.join(", ", items));

        ElderHealthNote note = healthNoteRepository.findByElderId(elderId).orElse(null);
        if (note == null) {
            healthNoteRepository.save(ElderHealthNote.builder()
                    .elderId(elderId)
                    .contentMd("## 최근 상태" + line)
                    .build());
        } else {
            note.update(note.getContentMd() + line);
        }
        return true;
    }

    // ---- JSON 파싱 헬퍼 ----

    /**
     * 응답은 Structured Outputs 로 스키마가 강제되므로 항상 유효한 JSON 이다.
     * (코드펜스 제거 같은 방어는 불필요) API 오류 등 예외 상황만 방어한다.
     */
    private JsonNode parseJson(String content) {
        try {
            return objectMapper.readTree(content == null ? "{}" : content);
        } catch (JacksonException e) {
            log.error("문서 판독 결과 JSON 파싱 실패: {}", content, e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "문서를 인식하지 못했습니다. 더 선명한 이미지로 다시 시도하세요.");
        }
    }

    private String text(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        String s = n.asString();
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 스키마가 integer 를 강제하므로 타입 변환 방어가 필요 없다. */
    private Integer intOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || !n.isNumber()) {
            return null;
        }
        return n.intValue();
    }

    private LocalDate dateOrNull(JsonNode n) {
        String s = text(n);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
