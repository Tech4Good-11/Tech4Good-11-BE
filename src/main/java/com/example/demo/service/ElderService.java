package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.Elder;
import com.example.demo.domain.GuardianElder;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.domain.enums.Relationship;
import com.example.demo.dto.elder.ElderCreateRequest;
import com.example.demo.dto.elder.ElderResponse;
import com.example.demo.dto.elder.ElderSummaryResponse;
import com.example.demo.dto.elder.ElderUpdateRequest;
import com.example.demo.domain.AgentConversation;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.repository.ElderRepository;
import com.example.demo.repository.GuardianElderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElderService {

    private final ElderRepository elderRepository;
    private final GuardianElderRepository guardianElderRepository;
    private final ElderDiseaseRepository diseaseRepository;
    private final ElderMedicationRepository medicationRepository;
    private final AgentConversationRepository conversationRepository;
    private final OwnershipService ownershipService;

    @Transactional
    public ElderResponse create(Long userId, ElderCreateRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이름은 필수입니다.");
        }
        Elder elder = elderRepository.save(Elder.builder()
                .name(request.name())
                .birthDate(request.birthDate())
                .gender(request.gender())
                .phone(request.phone())
                .build());

        Relationship relationship = request.relationship() != null ? request.relationship() : Relationship.other;
        guardianElderRepository.save(GuardianElder.builder()
                .userId(userId)
                .elderId(elder.getId())
                .relationship(relationship)
                .build());

        return ElderResponse.from(elder, relationship);
    }

    public List<ElderSummaryResponse> listMyElders(Long userId) {
        List<GuardianElder> links = guardianElderRepository.findByUserId(userId);
        return links.stream().map(link -> {
            Elder elder = elderRepository.findById(link.getElderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            long medCount = medicationRepository.countByElderIdAndStatus(elder.getId(), MedicationStatus.active);
            long diseaseCount = diseaseRepository.countByElderIdAndStatusNot(elder.getId(), DiseaseStatus.resolved);
            LocalDateTime lastCheckin = conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(elder.getId())
                    .stream()
                    .filter(c -> c.getPurpose() == com.example.demo.domain.enums.ConversationPurpose.daily_checkin)
                    .map(AgentConversation::getCreatedAt)
                    .findFirst()
                    .orElse(null);
            return ElderSummaryResponse.of(elder, link.getRelationship(), medCount, diseaseCount, lastCheckin);
        }).toList();
    }

    public ElderResponse getDetail(Long userId, Long elderId) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        Relationship relationship = guardianElderRepository.findByUserIdAndElderId(userId, elderId)
                .map(GuardianElder::getRelationship)
                .orElse(null);
        return ElderResponse.from(elder, relationship);
    }

    @Transactional
    public ElderResponse update(Long userId, Long elderId, ElderUpdateRequest request) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        elder.update(request.name(), request.birthDate(), request.gender(), request.phone());
        Relationship relationship = guardianElderRepository.findByUserIdAndElderId(userId, elderId)
                .map(GuardianElder::getRelationship)
                .orElse(null);
        return ElderResponse.from(elder, relationship);
    }

    @Transactional
    public void delete(Long userId, Long elderId) {
        ownershipService.verifyAndGetElder(userId, elderId);
        // FK CASCADE 로 연관 데이터도 함께 삭제됨
        elderRepository.deleteById(elderId);
    }
}
