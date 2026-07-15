package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.dto.disease.DiseaseRequest;
import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.repository.ElderDiseaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiseaseService {

    private final ElderDiseaseRepository diseaseRepository;
    private final OwnershipService ownershipService;

    public List<DiseaseResponse> list(Long userId, Long elderId, DiseaseStatus status) {
        ownershipService.verify(userId, elderId);
        List<ElderDisease> diseases = (status == null)
                ? diseaseRepository.findByElderId(elderId)
                : diseaseRepository.findByElderIdAndStatus(elderId, status);
        return diseases.stream().map(DiseaseResponse::from).toList();
    }

    @Transactional
    public DiseaseResponse create(Long userId, Long elderId, DiseaseRequest request) {
        ownershipService.verify(userId, elderId);
        if (request.diseaseName() == null || request.diseaseName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "질병명은 필수입니다.");
        }
        ElderDisease disease = ElderDisease.builder()
                .elderId(elderId)
                .diseaseName(request.diseaseName())
                .icdCode(request.icdCode())
                .diagnosedAt(request.diagnosedAt())
                .status(request.status() != null ? request.status() : DiseaseStatus.active)
                .notes(request.notes())
                .build();
        return DiseaseResponse.from(diseaseRepository.save(disease));
    }

    @Transactional
    public DiseaseResponse update(Long userId, Long elderId, Long diseaseId, DiseaseRequest request) {
        ownershipService.verify(userId, elderId);
        ElderDisease disease = getOwnedDisease(elderId, diseaseId);
        disease.update(
                request.diseaseName(),
                request.icdCode(),
                request.diagnosedAt(),
                request.status() != null ? request.status() : disease.getStatus(),
                request.notes()
        );
        return DiseaseResponse.from(disease);
    }

    @Transactional
    public void delete(Long userId, Long elderId, Long diseaseId) {
        ownershipService.verify(userId, elderId);
        ElderDisease disease = getOwnedDisease(elderId, diseaseId);
        diseaseRepository.delete(disease);
    }

    private ElderDisease getOwnedDisease(Long elderId, Long diseaseId) {
        ElderDisease disease = diseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!disease.getElderId().equals(elderId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return disease;
    }
}
