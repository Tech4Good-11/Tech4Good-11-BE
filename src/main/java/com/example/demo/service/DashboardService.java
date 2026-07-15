package com.example.demo.service;

import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.enums.ConversationPurpose;
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

    public DashboardResponse getDashboard(Long userId, Long elderId) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);

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

        List<DashboardResponse.CheckinBrief> recentCheckins = conversationRepository
                .findTop5ByElderIdOrderByCreatedAtDesc(elderId).stream()
                .filter(c -> c.getPurpose() == ConversationPurpose.daily_checkin)
                .map(this::toCheckinBrief)
                .toList();

        return new DashboardResponse(elderBrief, noteBrief, diseases, medications, todayReminders, recentCheckins);
    }

    private DashboardResponse.CheckinBrief toCheckinBrief(AgentConversation c) {
        // 간단 요약: transcript 앞부분을 잘라 요약 문자열로 사용
        String t = c.getTranscript() == null ? "" : c.getTranscript();
        String summary = t.length() > 120 ? t.substring(0, 120) + "..." : t;
        return new DashboardResponse.CheckinBrief(c.getId(), c.getPurpose().name(), c.getCreatedAt(), summary);
    }
}
