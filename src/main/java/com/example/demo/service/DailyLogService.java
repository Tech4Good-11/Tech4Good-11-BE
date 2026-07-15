package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.ElderDailyLog;
import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.ElderMedicationIntake;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.checkin.CheckinSubmitRequest;
import com.example.demo.dto.dailylog.DailyLogResponse;
import com.example.demo.dto.dailylog.DailyLogUpdateRequest;
import com.example.demo.dto.dailylog.MedicationIntakeRequest;
import com.example.demo.dto.dailylog.MedicationIntakeResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDailyLogRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderMedicationIntakeRepository;
import com.example.demo.repository.ElderMedicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하루 생활 로그(수면/운동/AI요약/체크리스트)와 복약 여부를 관리한다.
 * 값의 원천은 (1) 에이전트 대화에서 LLM 추출, (2) 보호자 수동 입력/체크 두 가지다.
 *
 * 설계 원칙: "대화로 알아낼 수 있는 것만" 보관한다.
 *  - 수면시간/운동시간(분)/복약여부/질병 현재상황 → 자가보고로 확인 가능
 *  - 걸음수 → 대화로 알 수 없어 보관하지 않는다(웨어러블 연동 시 별도 확장)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyLogService {

    private final OpenAiClient openAiClient;
    private final OwnershipService ownershipService;
    private final ElderDailyLogRepository dailyLogRepository;
    private final ElderMedicationIntakeRepository intakeRepository;
    private final ElderMedicationRepository medicationRepository;
    private final ElderDiseaseRepository diseaseRepository;
    private final AgentConversationRepository conversationRepository;
    private final ReminderService reminderService;
    private final ObjectMapper objectMapper;

    // ---------- 조회 ----------

    public DailyLogResponse get(Long userId, Long elderId, LocalDate date) {
        ownershipService.verify(userId, elderId);
        return readLog(elderId, date == null ? LocalDate.now() : date);
    }

    /** 소유권 검증을 마친 호출자용(대시보드). 없으면 빈 로그(모두 null)를 반환. */
    public DailyLogResponse readLog(Long elderId, LocalDate date) {
        return dailyLogRepository.findByElderIdAndLogDate(elderId, date)
                .map(this::toResponse)
                .orElse(new DailyLogResponse(elderId, date, null, null, null, List.of(), null, null));
    }

    public List<MedicationIntakeResponse> getIntakes(Long userId, Long elderId, LocalDate date) {
        ownershipService.verify(userId, elderId);
        return readMedicationIntakes(elderId, date == null ? LocalDate.now() : date);
    }

    /** 활성 약 전체 + 해당 일자 복용여부(기록 없으면 taken=null → '미확인'). */
    public List<MedicationIntakeResponse> readMedicationIntakes(Long elderId, LocalDate date) {
        List<ElderMedication> meds = medicationRepository.findByElderIdAndStatus(elderId, MedicationStatus.active);
        Map<Long, Boolean> takenByMedId = new LinkedHashMap<>();
        for (ElderMedicationIntake intake : intakeRepository.findByElderIdAndIntakeDate(elderId, date)) {
            takenByMedId.put(intake.getMedicationId(), intake.getTaken());
        }
        List<MedicationIntakeResponse> result = new ArrayList<>();
        for (ElderMedication m : meds) {
            result.add(new MedicationIntakeResponse(
                    m.getId(), m.getMedicationName(), m.getDosage(),
                    takenByMedId.get(m.getId()), date));
        }
        return result;
    }

    // ---------- 수동 입력 ----------

    @Transactional
    public DailyLogResponse upsert(Long userId, Long elderId, DailyLogUpdateRequest request) {
        ownershipService.verify(userId, elderId);
        LocalDate date = request.logDate() == null ? LocalDate.now() : request.logDate();
        upsertLog(elderId, date, request.sleepHours(), request.exerciseMinutes(),
                request.conditionSummary(), null, null);
        return readLog(elderId, date);
    }

    /** 체크리스트의 복약 체크박스 연동. */
    @Transactional
    public MedicationIntakeResponse recordIntake(Long userId, Long elderId, MedicationIntakeRequest request) {
        ownershipService.verify(userId, elderId);
        if (request.medicationId() == null || request.taken() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "medicationId 와 taken 은 필수입니다.");
        }
        ElderMedication med = medicationRepository.findById(request.medicationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "약을 찾을 수 없습니다."));
        if (!med.getElderId().equals(elderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDate date = request.intakeDate() == null ? LocalDate.now() : request.intakeDate();
        upsertIntake(elderId, med.getId(), date, request.taken(), null);
        return new MedicationIntakeResponse(med.getId(), med.getMedicationName(), med.getDosage(),
                request.taken(), date);
    }

    // ---------- 체크인 응답 반영 (LLM 불필요) ----------

    /** POST /checkin 의 응답을 체크리스트 상태 + 복약여부로 반영한다. */
    @Transactional
    public void applyCheckinAnswers(Long elderId, String elderName,
                                    List<CheckinSubmitRequest.Answer> answers, Long conversationId) {
        LocalDate today = LocalDate.now();

        // 1) 체크리스트 응답 저장(ruleCode -> yes/no)
        ObjectNode checklist = objectMapper.createObjectNode();
        for (CheckinSubmitRequest.Answer a : answers) {
            if (a.ruleCode() != null && a.answer() != null) {
                checklist.put(a.ruleCode(), a.answer());
            }
        }
        String checklistJson = writeJsonQuietly(checklist);

        // 2) 복약 규칙(ruleType=medication) 응답 -> 해당 약의 복용여부로 반영
        var reminders = reminderService.matchRules(elderId, elderName);
        List<ElderMedication> meds = medicationRepository.findByElderIdAndStatus(elderId, MedicationStatus.active);
        for (CheckinSubmitRequest.Answer a : answers) {
            reminders.stream()
                    .filter(r -> r.ruleCode().equals(a.ruleCode()))
                    .filter(r -> r.matchedBy() != null && "medication".equals(r.matchedBy().target()))
                    .findFirst()
                    .ifPresent(r -> findMedication(meds, r.matchedBy().medicationName())
                            .ifPresent(m -> upsertIntake(elderId, m.getId(), today,
                                    "yes".equalsIgnoreCase(a.answer()), conversationId)));
        }

        upsertLog(elderId, today, null, null, null, checklistJson, conversationId);
    }

    // ---------- 대화에서 추출 (LLM) ----------

    /**
     * 대화 내용을 LLM 으로 분석해 수면/운동/요약/복약여부/질병 현재상황을 추출·저장한다.
     * conversationId 가 null 이면 가장 최근 대화를 사용한다.
     */
    @Transactional
    public DailyLogResponse extractFromConversation(Long userId, Long elderId, Long conversationId) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        AgentConversation conv = resolveConversation(elderId, conversationId);
        if (conv == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "분석할 대화가 없습니다.");
        }
        extractInternal(elder, conv);
        return readLog(elderId, LocalDate.now());
    }

    /** 대화 저장 직후 자동 호출용(실패해도 본 요청은 성공시킨다). */
    @Transactional
    public void extractQuietly(Elder elder, AgentConversation conv) {
        try {
            extractInternal(elder, conv);
        } catch (Exception e) {
            log.warn("대화 지표 추출 실패(무시): elderId={}, convId={}", elder.getId(), conv.getId(), e);
        }
    }

    private void extractInternal(Elder elder, AgentConversation conv) {
        Long elderId = elder.getId();
        List<ElderMedication> meds = medicationRepository.findByElderIdAndStatus(elderId, MedicationStatus.active);
        List<ElderDisease> diseases = new ArrayList<>(diseaseRepository.findByElderIdAndStatus(elderId, DiseaseStatus.active));
        diseases.addAll(diseaseRepository.findByElderIdAndStatus(elderId, DiseaseStatus.managed));

        String prompt = buildExtractionPrompt(elder.getName(), meds, diseases, conv.getTranscript());
        String content = openAiClient.chat(
                "너는 대화 내용에서 사실만 추출하는 분석기다. 반드시 유효한 JSON 만 출력한다.",
                List.of(new OpenAiClient.ChatMessage("user", prompt)));

        JsonNode extracted = parseJson(content);
        LocalDate today = LocalDate.now();

        // 1) 수면/운동/요약
        BigDecimal sleepHours = decimalOrNull(extracted.path("sleepHours"));
        Integer exerciseMinutes = intOrNull(extracted.path("exerciseMinutes"));
        String summary = textOrNull(extracted.path("conditionSummary"));
        upsertLog(elderId, today, sleepHours, exerciseMinutes, summary, null, conv.getId());

        // 2) 복약 여부
        JsonNode medsTaken = extracted.path("medicationsTaken");
        if (medsTaken.isArray()) {
            for (JsonNode node : medsTaken) {
                String name = textOrNull(node.path("medicationName"));
                JsonNode takenNode = node.path("taken");
                if (name == null || takenNode.isMissingNode() || takenNode.isNull()) {
                    continue;
                }
                findMedication(meds, name).ifPresent(m ->
                        upsertIntake(elderId, m.getId(), today, takenNode.asBoolean(), conv.getId()));
            }
        }

        // 3) 질병 현재상황 -> elder_disease.notes 갱신(기존 컬럼 재사용)
        JsonNode diseaseUpdates = extracted.path("diseaseUpdates");
        if (diseaseUpdates.isArray()) {
            for (JsonNode node : diseaseUpdates) {
                String name = textOrNull(node.path("diseaseName"));
                String note = textOrNull(node.path("note"));
                if (name == null || note == null) {
                    continue;
                }
                diseases.stream()
                        .filter(d -> matches(d.getDiseaseName(), name))
                        .findFirst()
                        .ifPresent(d -> d.update(d.getDiseaseName(), d.getIcdCode(), d.getDiagnosedAt(),
                                d.getStatus(), note));
            }
        }
    }

    private String buildExtractionPrompt(String elderName, List<ElderMedication> meds,
                                         List<ElderDisease> diseases, String transcript) {
        String medNames = meds.isEmpty() ? "(없음)"
                : String.join(", ", meds.stream().map(ElderMedication::getMedicationName).toList());
        String diseaseNames = diseases.isEmpty() ? "(없음)"
                : String.join(", ", diseases.stream().map(ElderDisease::getDiseaseName).toList());

        return "아래는 어르신(" + elderName + ")과 AI 에이전트의 대화 기록(JSON)이다.\n"
                + "이 대화에서 **명시적으로 언급된 사실만** 추출하여 아래 JSON 형식으로만 답하라(설명 금지).\n\n"
                + "어르신의 복용 약 목록: " + medNames + "\n"
                + "어르신의 질병 목록: " + diseaseNames + "\n\n"
                + "대화 기록:\n" + transcript + "\n\n"
                + "출력 형식:\n"
                + "{\n"
                + "  \"sleepHours\": 6.5,\n"
                + "  \"exerciseMinutes\": 30,\n"
                + "  \"conditionSummary\": \"한 문장 요약\",\n"
                + "  \"medicationsTaken\": [ {\"medicationName\": \"위 약 목록 중 하나\", \"taken\": true} ],\n"
                + "  \"diseaseUpdates\": [ {\"diseaseName\": \"위 질병 목록 중 하나\", \"note\": \"현재 상황 한 줄\"} ]\n"
                + "}\n"
                + "규칙:\n"
                + "- 대화에 언급이 없으면 해당 값은 null, 배열은 빈 배열([]). 절대 추측하지 마라.\n"
                + "- sleepHours 는 시간 단위 숫자(예: 6.5), exerciseMinutes 는 분 단위 정수(예: 30).\n"
                + "- 걸음수는 추출하지 마라(대화로 알 수 없음).\n"
                + "- medicationName/diseaseName 은 반드시 위 목록에 있는 이름을 그대로 사용하라.\n"
                + "- conditionSummary 는 보호자가 읽을 한국어 한 문장.";
    }

    // ---------- 내부 헬퍼 ----------

    private AgentConversation resolveConversation(Long elderId, Long conversationId) {
        if (conversationId != null) {
            AgentConversation conv = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (!conv.getElderId().equals(elderId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return conv;
        }
        return conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(elderId).stream()
                .findFirst().orElse(null);
    }

    private void upsertLog(Long elderId, LocalDate date, BigDecimal sleepHours, Integer exerciseMinutes,
                           String summary, String checklistJson, Long conversationId) {
        ElderDailyLog log = dailyLogRepository.findByElderIdAndLogDate(elderId, date).orElse(null);
        if (log == null) {
            dailyLogRepository.save(ElderDailyLog.builder()
                    .elderId(elderId)
                    .logDate(date)
                    .sleepHours(sleepHours)
                    .exerciseMinutes(exerciseMinutes)
                    .conditionSummary(summary)
                    .checklistAnswers(checklistJson)
                    .sourceConversationId(conversationId)
                    .build());
        } else {
            log.patch(sleepHours, exerciseMinutes, summary, checklistJson, conversationId);
        }
    }

    private void upsertIntake(Long elderId, Long medicationId, LocalDate date, boolean taken, Long conversationId) {
        intakeRepository.findByMedicationIdAndIntakeDate(medicationId, date)
                .ifPresentOrElse(
                        i -> i.update(taken, conversationId),
                        () -> intakeRepository.save(ElderMedicationIntake.builder()
                                .elderId(elderId)
                                .medicationId(medicationId)
                                .intakeDate(date)
                                .taken(taken)
                                .sourceConversationId(conversationId)
                                .build()));
    }

    private java.util.Optional<ElderMedication> findMedication(List<ElderMedication> meds, String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        return meds.stream().filter(m -> matches(m.getMedicationName(), name)).findFirst();
    }

    private boolean matches(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim();
        String y = b.trim();
        return x.equalsIgnoreCase(y) || x.contains(y) || y.contains(x);
    }

    private DailyLogResponse toResponse(ElderDailyLog entity) {
        List<DailyLogResponse.ChecklistAnswer> checklist = new ArrayList<>();
        String answers = entity.getChecklistAnswers();
        if (answers != null && !answers.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(answers);
                node.propertyNames().forEach(k ->
                        checklist.add(new DailyLogResponse.ChecklistAnswer(k, node.path(k).asString())));
            } catch (JacksonException e) {
                log.warn("checklist_answers JSON 파싱 실패: elderId={}, date={}, raw={}",
                        entity.getElderId(), entity.getLogDate(), answers, e);
            }
        }
        return new DailyLogResponse(
                entity.getElderId(), entity.getLogDate(), entity.getSleepHours(), entity.getExerciseMinutes(),
                entity.getConditionSummary(), checklist, entity.getSourceConversationId(), entity.getUpdatedAt());
    }

    private String writeJsonQuietly(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            return null;
        }
    }

    private JsonNode parseJson(String content) {
        try {
            String t = content == null ? "{}" : content.trim();
            if (t.startsWith("```")) {
                t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
            }
            return objectMapper.readTree(t);
        } catch (JacksonException e) {
            log.error("대화 추출 결과 JSON 파싱 실패: {}", content, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "대화 분석 결과를 이해하지 못했습니다.");
        }
    }

    private String textOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        String s = n.asString();
        return (s == null || s.isBlank()) ? null : s;
    }

    private Integer intOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || !n.isNumber()) {
            return null;
        }
        return n.intValue();
    }

    private BigDecimal decimalOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || !n.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(n.doubleValue());
    }
}
