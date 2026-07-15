package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 진단서/처방전 문서 처리.
 * MOCK: 실제 OCR/LLM 없이 docType 에 따른 "정해진 예시 결과"를 반환하되,
 *       그 결과를 실제로 elder_disease / elder_medication / agent_conversation 에 저장하여
 *       전체 흐름(대시보드 반영 등)이 확인되도록 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

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

        List<MedicationResponse> extractedMedications = new ArrayList<>();
        List<DiseaseResponse> extractedDiseases = new ArrayList<>();

        // MOCK: docType 별 정해진 예시 인식 결과를 실데이터로 저장
        if (docType.equals("prescription")) {
            ElderMedication med = medicationRepository.save(ElderMedication.builder()
                    .elderId(elderId)
                    .medicationName("아빌리파이")
                    .atcCode("N05AX12")
                    .dosage("5mg 1정")
                    .intervalHours(24)
                    .startDate(LocalDate.now())
                    .endDate(null)
                    .status(MedicationStatus.active)
                    .build());
            extractedMedications.add(MedicationResponse.from(med));
        } else { // diagnosis
            ElderDisease disease = diseaseRepository.save(ElderDisease.builder()
                    .elderId(elderId)
                    .diseaseName("위염")
                    .icdCode("K29")
                    .diagnosedAt(LocalDate.now())
                    .status(DiseaseStatus.active)
                    .notes("문서 처리로 자동 등록(MOCK)")
                    .build());
            extractedDiseases.add(DiseaseResponse.from(disease));
        }

        // 처리 과정을 document_intake 대화로 기록
        String transcript = buildTranscript(docType, file.getOriginalFilename());
        AgentConversation conv = conversationRepository.save(AgentConversation.builder()
                .elderId(elderId)
                .purpose(ConversationPurpose.document_intake)
                .transcript(transcript)
                .build());

        // MOCK: 건강 노트 자동 갱신(LLM 대체) — 없으면 생성, 있으면 안내 라인 추가
        boolean noteUpdated = updateHealthNoteMock(elderId, docType, elder.getName());

        return new DocumentIntakeResponse(
                conv.getId(),
                docType,
                extractedMedications,
                extractedDiseases,
                noteUpdated
        );
    }

    private String buildTranscript(String docType, String filename) {
        ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode agent = objectMapper.createObjectNode();
        agent.put("role", "agent");
        agent.put("text", "MOCK: " + docType + " 문서(" + (filename == null ? "unknown" : filename) + ") 를 인식했습니다.");
        arr.add(agent);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("role", "system");
        result.put("text", "MOCK OCR/LLM 결과를 실데이터로 저장했습니다.");
        arr.add(result);
        try {
            return objectMapper.writeValueAsString(arr);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    // MOCK: 실제로는 LLM 이 대화/문서 종합 후 갱신
    private boolean updateHealthNoteMock(Long elderId, String docType, String elderName) {
        ElderHealthNote note = healthNoteRepository.findByElderId(elderId).orElse(null);
        String line = "\n- (자동) " + docType + " 문서 처리 반영됨 (MOCK)";
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
}
