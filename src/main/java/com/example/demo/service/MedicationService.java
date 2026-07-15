package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.medication.MedicationRequest;
import com.example.demo.dto.medication.MedicationResponse;
import com.example.demo.repository.ElderMedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private final ElderMedicationRepository medicationRepository;
    private final OwnershipService ownershipService;

    public List<MedicationResponse> list(Long userId, Long elderId, MedicationStatus status) {
        ownershipService.verify(userId, elderId);
        List<ElderMedication> meds = (status == null)
                ? medicationRepository.findByElderId(elderId)
                : medicationRepository.findByElderIdAndStatus(elderId, status);
        return meds.stream().map(MedicationResponse::from).toList();
    }

    @Transactional
    public MedicationResponse create(Long userId, Long elderId, MedicationRequest request) {
        ownershipService.verify(userId, elderId);
        if (request.medicationName() == null || request.medicationName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "약품명은 필수입니다.");
        }
        ElderMedication med = ElderMedication.builder()
                .elderId(elderId)
                .medicationName(request.medicationName())
                .atcCode(request.atcCode())
                .dosage(request.dosage())
                .intervalHours(request.intervalHours())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status() != null ? request.status() : MedicationStatus.active)
                .build();
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional
    public MedicationResponse update(Long userId, Long elderId, Long medicationId, MedicationRequest request) {
        ownershipService.verify(userId, elderId);
        ElderMedication med = getOwnedMedication(elderId, medicationId);
        med.update(
                request.medicationName(),
                request.atcCode(),
                request.dosage(),
                request.intervalHours(),
                request.startDate(),
                request.endDate(),
                request.status() != null ? request.status() : med.getStatus()
        );
        return MedicationResponse.from(med);
    }

    @Transactional
    public void delete(Long userId, Long elderId, Long medicationId) {
        ownershipService.verify(userId, elderId);
        ElderMedication med = getOwnedMedication(elderId, medicationId);
        medicationRepository.delete(med);
    }

    private ElderMedication getOwnedMedication(Long elderId, Long medicationId) {
        ElderMedication med = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!med.getElderId().equals(elderId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return med;
    }
}
