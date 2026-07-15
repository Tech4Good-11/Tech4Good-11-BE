package com.example.demo.service;

import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.dto.dailylog.DailyLogResponse;
import com.example.demo.dto.dailylog.MedicationIntakeResponse;
import com.example.demo.dto.dashboard.DashboardResponse;
import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.dto.medication.MedicationResponse;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OwnershipService ownershipService;
    private final ElderHealthNoteRepository healthNoteRepository;
    private final ElderDiseaseRepository diseaseRepository;
    private final ElderMedicationRepository medicationRepository;
    private final AgentConversationRepository conversationRepository;
    private final ReminderService reminderService;
    private final DailyLogService dailyLogService;

    public DashboardResponse getDashboard(Long userId, Long elderId) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        LocalDate today = LocalDate.now();

        DashboardResponse.ElderBrief elderBrief = new DashboardResponse.ElderBrief(
                elder.getId(), elder.getName(), elder.getBirthDate(),
                elder.getGender() != null ? elder.getGender().name() : null);

        DashboardResponse.HealthNoteBrief noteBrief = healthNoteRepository.findByElderId(elderId)
                .map(n -> new DashboardResponse.HealthNoteBrief(n.getContentMd(), n.getUpdatedAt()))
                .orElse(null);

        List<DiseaseResponse> diseases = diseaseRepository.findByElderId(elderId).stream()
                .map(DiseaseResponse::from).toList();

        List<MedicationResponse> medications = medicationRepository.findByElderId(elderId).stream()
                .map(MedicationResponse::from).toList();

        List<ElderReminderResponse> todayReminders = reminderService.matchRules(elderId, elder.getName());

        // 대화에서 추출된 오늘 지표
        DailyLogResponse dailyLog = dailyLogService.readLog(elderId, today);
        List<MedicationIntakeResponse> todayMedications = dailyLogService.readMedicationIntakes(elderId, today);

        List<DashboardResponse.CheckinBrief> recentCheckins = conversationRepository
                .findTop5ByElderIdOrderByCreatedAtDesc(elderId).stream()
                .filter(c -> c.getPurpose() == ConversationPurpose.daily_checkin
                        || c.getPurpose() == ConversationPurpose.free)
                .map(c -> toCheckinBrief(c, dailyLog))
                .toList();

        DashboardResponse.HealthScore healthScore = computeHealthScore(dailyLog, todayMedications);

        return new DashboardResponse(elderBrief, noteBrief, diseases, medications, todayReminders,
                recentCheckins, dailyLog, todayMedications, healthScore);
    }

    /** AI 상담 요약: 대화에서 추출한 conditionSummary 를 우선 사용(없으면 null → 프론트가 빈 상태 처리). */
    private DashboardResponse.CheckinBrief toCheckinBrief(AgentConversation c, DailyLogResponse dailyLog) {
        String summary = null;
        if (dailyLog != null
                && dailyLog.sourceConversationId() != null
                && dailyLog.sourceConversationId().equals(c.getId())) {
            summary = dailyLog.conditionSummary();
        }
        return new DashboardResponse.CheckinBrief(c.getId(), c.getPurpose().name(), c.getCreatedAt(), summary);
    }

    /**
     * 건강 점수 산출(파생값). 근거가 없는 항목은 null 로 두고 평균에서 제외한다.
     * - 복약: 오늘 복용 확인된 약 / 복용 여부가 확인된 약  (전부 미확인이면 null)
     * - 수면: 7시간을 100점 기준, 1시간 차이당 -25점
     * - 운동: 30분 이상이면 100점, 비례 배분
     */
    private DashboardResponse.HealthScore computeHealthScore(DailyLogResponse dailyLog,
                                                             List<MedicationIntakeResponse> todayMedications) {
        Integer medicationScore = null;
        long known = todayMedications.stream().filter(m -> m.taken() != null).count();
        if (known > 0) {
            long taken = todayMedications.stream().filter(m -> Boolean.TRUE.equals(m.taken())).count();
            medicationScore = (int) Math.round((taken * 100.0) / known);
        }

        Integer sleepScore = null;
        BigDecimal sleep = dailyLog == null ? null : dailyLog.sleepHours();
        if (sleep != null) {
            double diff = Math.abs(sleep.doubleValue() - 7.0);
            sleepScore = (int) Math.round(Math.max(0, 100 - diff * 25));
        }

        Integer exerciseScore = null;
        Integer minutes = dailyLog == null ? null : dailyLog.exerciseMinutes();
        if (minutes != null) {
            exerciseScore = (int) Math.round(Math.min(100.0, (minutes * 100.0) / 30.0));
        }

        List<Integer> parts = new ArrayList<>();
        if (medicationScore != null) {
            parts.add(medicationScore);
        }
        if (sleepScore != null) {
            parts.add(sleepScore);
        }
        if (exerciseScore != null) {
            parts.add(exerciseScore);
        }

        Integer score = parts.isEmpty() ? null
                : (int) Math.round(parts.stream().mapToInt(Integer::intValue).average().orElse(0));

        String comment = buildComment(score, medicationScore, sleepScore, exerciseScore);
        return new DashboardResponse.HealthScore(score, medicationScore, sleepScore, exerciseScore, comment);
    }

    private String buildComment(Integer score, Integer med, Integer sleep, Integer exercise) {
        if (score == null) {
            return "오늘 기록이 아직 없어요. 대화를 나누면 점수가 계산됩니다.";
        }
        List<String> missing = new ArrayList<>();
        if (med == null) {
            missing.add("복약");
        }
        if (sleep == null) {
            missing.add("수면");
        }
        if (exercise == null) {
            missing.add("운동");
        }
        String base = score >= 80 ? "오늘 건강 관리가 잘 되고 있어요."
                : score >= 50 ? "대체로 괜찮지만 조금 더 챙기면 좋겠어요."
                : "오늘은 관리가 부족했어요.";
        return missing.isEmpty() ? base : base + " (" + String.join("·", missing) + " 기록 없음)";
    }
}
