package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.ElderDailyLog;
import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderHealthNote;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.consult.ConsultRequest;
import com.example.demo.dto.consult.ConsultResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDailyLogRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 자녀(보호자) 상담. 어르신이 남긴 기록을 근거로 자녀의 질문에 답한다.
 *
 * <p>어르신 대화({@link ChatService})와 목적이 완전히 다르다:
 * <ul>
 *   <li>말하는 사람이 자녀이므로 어르신을 <b>3인칭</b>으로 지칭한다.</li>
 *   <li>어르신의 최근 <b>대화 기록</b>과 생활 로그를 컨텍스트에 넣어, "어머니가 머리 아프다고 하셨던데"
 *       같은 질문에 실제 기록을 근거로 답할 수 있게 한다.</li>
 *   <li>대화를 <b>저장하지 않고</b>, 건강 지표로 <b>추출하지도 않는다</b>.
 *       자녀의 발화는 어르신의 자가보고가 아니므로 저장/추출하면 어르신 데이터가 오염된다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultService {

    /** 컨텍스트에 넣을 대화 1건당 최대 길이(프롬프트 비대화 방지). */
    private static final int TRANSCRIPT_MAX_CHARS = 600;

    private final OpenAiClient openAiClient;
    private final OwnershipService ownershipService;
    private final ElderDiseaseRepository diseaseRepository;
    private final ElderMedicationRepository medicationRepository;
    private final ElderHealthNoteRepository healthNoteRepository;
    private final AgentConversationRepository conversationRepository;
    private final ElderDailyLogRepository dailyLogRepository;

    public ConsultResponse consult(Long userId, Long elderId, ConsultRequest request) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "message 는 필수입니다.");
        }

        List<OpenAiClient.ChatMessage> messages = new ArrayList<>();
        if (request.history() != null) {
            for (ConsultRequest.Turn turn : request.history()) {
                if (turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(turn.role()) ? "assistant" : "user";
                messages.add(new OpenAiClient.ChatMessage(role, turn.content()));
            }
        }
        messages.add(new OpenAiClient.ChatMessage("user", request.message()));

        String reply = openAiClient.chat(buildSystemPrompt(elder), messages);
        return new ConsultResponse(reply);
    }

    private String buildSystemPrompt(Elder elder) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 시니어 건강관리 앱 '온기'의 상담 AI다. ")
          .append("지금 대화 상대는 어르신 본인이 아니라 **어르신의 자녀(보호자)** 다.\n\n")
          .append("원칙:\n")
          .append("- 어르신을 3인칭으로 지칭한다(예: \"").append(elder.getName()).append("님께서는\"). 자녀에게 존댓말로 답한다.\n")
          .append("- 반드시 아래 [기록]에 근거해 답한다. 기록에 없는 내용을 지어내지 말고, 모르면 모른다고 말한다.\n")
          .append("- 의학적 진단이나 처방을 단정하지 않는다. 우려되는 신호가 보이면 의료진 상담을 권한다.\n")
          .append("- 자녀가 무엇을 하면 좋을지 구체적으로 제안한다.\n")
          .append("- 답변은 간결하게 한다.\n\n");

        sb.append("=== [기록] ===\n");
        sb.append("[어르신 정보]\n");
        sb.append("- 이름: ").append(elder.getName()).append("\n");
        if (elder.getBirthDate() != null) {
            sb.append("- 생년월일: ").append(elder.getBirthDate()).append("\n");
        }

        List<ElderDisease> diseases = diseaseRepository.findByElderIdAndStatus(elder.getId(), DiseaseStatus.active);
        if (!diseases.isEmpty()) {
            sb.append("- 현재 질환: ")
              .append(String.join(", ", diseases.stream().map(ElderDisease::getDiseaseName).toList()))
              .append("\n");
        }

        List<ElderMedication> medications =
                medicationRepository.findByElderIdAndStatus(elder.getId(), MedicationStatus.active);
        if (!medications.isEmpty()) {
            sb.append("- 복용 약: ")
              .append(String.join(", ", medications.stream()
                      .map(m -> m.getMedicationName() + (m.getDosage() != null ? "(" + m.getDosage() + ")" : ""))
                      .toList()))
              .append("\n");
        }

        ElderHealthNote note = healthNoteRepository.findByElderId(elder.getId()).orElse(null);
        if (note != null && note.getContentMd() != null && !note.getContentMd().isBlank()) {
            sb.append("\n[건강 노트]\n").append(note.getContentMd()).append("\n");
        }

        List<ElderDailyLog> logs = dailyLogRepository.findTop7ByElderIdOrderByLogDateDesc(elder.getId());
        if (!logs.isEmpty()) {
            sb.append("\n[최근 생활 기록]\n");
            for (ElderDailyLog log : logs) {
                sb.append("- ").append(log.getLogDate()).append(": ");
                sb.append("수면 ").append(log.getSleepHours() == null ? "기록없음" : log.getSleepHours() + "시간");
                sb.append(", 운동 ").append(log.getExerciseMinutes() == null ? "기록없음" : log.getExerciseMinutes() + "분");
                if (log.getConditionSummary() != null) {
                    sb.append(", 요약: ").append(log.getConditionSummary());
                }
                sb.append("\n");
            }
        }

        // 자녀 상담의 핵심 근거: 어르신이 에이전트와 실제로 나눈 대화
        List<AgentConversation> conversations =
                conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(elder.getId());
        if (!conversations.isEmpty()) {
            sb.append("\n[어르신의 최근 대화 기록]\n");
            for (AgentConversation c : conversations) {
                sb.append("- ").append(c.getCreatedAt()).append(" (").append(c.getPurpose().name()).append("): ")
                  .append(truncate(c.getTranscript()))
                  .append("\n");
            }
        }

        return sb.toString();
    }

    private String truncate(String transcript) {
        if (transcript == null) {
            return "";
        }
        return transcript.length() <= TRANSCRIPT_MAX_CHARS
                ? transcript
                : transcript.substring(0, TRANSCRIPT_MAX_CHARS) + "...(생략)";
    }
}
