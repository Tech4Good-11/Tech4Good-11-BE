package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.GuardianElder;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.Relationship;
import com.example.demo.dto.guardian.GuardianAddRequest;
import com.example.demo.dto.guardian.GuardianResponse;
import com.example.demo.repository.GuardianElderRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianService {

    private final GuardianElderRepository guardianElderRepository;
    private final UserRepository userRepository;
    private final OwnershipService ownershipService;

    public List<GuardianResponse> listGuardians(Long userId, Long elderId) {
        ownershipService.verify(userId, elderId);
        return guardianElderRepository.findByElderId(elderId).stream()
                .map(link -> {
                    User u = userRepository.findById(link.getUserId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
                    return GuardianResponse.of(u, link.getRelationship());
                })
                .toList();
    }

    @Transactional
    public GuardianResponse addGuardian(Long userId, Long elderId, GuardianAddRequest request) {
        ownershipService.verify(userId, elderId);
        User target = userRepository.findByEmail(request.email() == null ? "" : request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "해당 이메일의 보호자를 찾을 수 없습니다."));
        if (guardianElderRepository.existsByUserIdAndElderId(target.getId(), elderId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 보호자입니다.");
        }
        Relationship relationship = request.relationship() != null ? request.relationship() : Relationship.other;
        guardianElderRepository.save(GuardianElder.builder()
                .userId(target.getId())
                .elderId(elderId)
                .relationship(relationship)
                .build());
        return GuardianResponse.of(target, relationship);
    }

    @Transactional
    public void removeGuardian(Long userId, Long elderId, Long targetUserId) {
        ownershipService.verify(userId, elderId);
        GuardianElder link = guardianElderRepository.findByUserIdAndElderId(targetUserId, elderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "해당 보호관계가 없습니다."));
        guardianElderRepository.delete(link);
    }
}
