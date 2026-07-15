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
import com.example.demo.dto.chat.ChatRequest;
import com.example.demo.dto.chat.ChatResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 에이전트('온기') 챗봇. 어르신의 건강 컨텍스트(질병/복약/건강노트)를 시스템 프롬프트로 넣어
 * OpenAI 로 답변을 생성한다. 선택적으로 대화를 agent_conversation 에 저장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final OpenAiClient openAiClient;
    private final OwnershipService ownershipService;
    private final ElderDiseaseRepository diseaseRepository;
    private final ElderMedicationRepository medicationRepository;
    private final ElderHealthNoteRepository healthNoteRepository;
    private final AgentConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatResponse chat(Long userId, Long elderId, ChatRequest request) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "message 는 필수입니다.");
        }

        String systemPrompt = buildSystemPrompt(elder);

        List<OpenAiClient.ChatMessage> messages = new ArrayList<>();
        if (request.history() != null) {
            for (ChatRequest.Turn turn : request.history()) {
                if (turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(turn.role()) ? "assistant" : "user";
                messages.add(new OpenAiClient.ChatMessage(role, turn.content()));
            }
        }
        messages.add(new OpenAiClient.ChatMessage("user", request.message()));

        String reply = openAiClient.chat(systemPrompt, messages);

        Long conversationId = null;
        if (Boolean.TRUE.equals(request.save())) {
            conversationId = saveConversation(elderId, request, reply);
        }
        return new ChatResponse(reply, conversationId);
    }

    private String buildSystemPrompt(Elder elder) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 시니어 건강관리 앱 '온기'의 다정한 AI 에이전트다. ")
          .append("어르신을 존중하는 따뜻하고 쉬운 말투(존댓말)로 짧고 명확하게 대화한다. ")
          .append("의학적 진단이나 처방을 단정하지 말고, 필요하면 보호자·의료진과 상의하도록 부드럽게 안내한다.\n\n");

        sb.append("[어르신 정보]\n");
        sb.append("- 이름: ").append(elder.getName()).append("\n");
        if (elder.getBirthDate() != null) {
            sb.append("- 생년월일: ").append(elder.getBirthDate()).append("\n");
        }

        List<ElderDisease> diseases = diseaseRepository.findByElderIdAndStatus(elder.getId(), DiseaseStatus.active);
        if (!diseases.isEmpty()) {
            sb.append("- 현재 질환: ");
            sb.append(String.join(", ", diseases.stream().map(ElderDisease::getDiseaseName).toList()));
            sb.append("\n");
        }

        List<ElderMedication> medications = medicationRepository.findByElderIdAndStatus(elder.getId(), MedicationStatus.active);
        if (!medications.isEmpty()) {
            sb.append("- 복용 약: ");
            sb.append(String.join(", ", medications.stream()
                    .map(m -> m.getMedicationName() + (m.getDosage() != null ? "(" + m.getDosage() + ")" : ""))
                    .toList()));
            sb.append("\n");
        }

        ElderHealthNote note = healthNoteRepository.findByElderId(elder.getId()).orElse(null);
        if (note != null && note.getContentMd() != null && !note.getContentMd().isBlank()) {
            sb.append("\n[건강 노트]\n").append(note.getContentMd()).append("\n");
        }

        return sb.toString();
    }

    private Long saveConversation(Long elderId, ChatRequest request, String reply) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (request.history() != null) {
            for (ChatRequest.Turn turn : request.history()) {
                if (turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                ObjectNode node = arr.addObject();
                node.put("role", "assistant".equals(turn.role()) ? "agent" : "elder");
                node.put("text", turn.content());
            }
        }
        ObjectNode userTurn = arr.addObject();
        userTurn.put("role", "elder");
        userTurn.put("text", request.message());
        ObjectNode agentTurn = arr.addObject();
        agentTurn.put("role", "agent");
        agentTurn.put("text", reply);

        String json;
        try {
            json = objectMapper.writeValueAsString(arr);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "대화 저장 직렬화 실패");
        }

        ConversationPurpose purpose = request.purpose() != null ? request.purpose() : ConversationPurpose.free;
        AgentConversation conv = conversationRepository.save(AgentConversation.builder()
                .elderId(elderId)
                .purpose(purpose)
                .transcript(json)
                .build());
        return conv.getId();
    }
}
