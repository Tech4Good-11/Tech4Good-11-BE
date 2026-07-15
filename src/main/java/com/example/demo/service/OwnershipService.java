package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.Elder;
import com.example.demo.repository.ElderRepository;
import com.example.demo.repository.GuardianElderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유권 검증 헬퍼. 로그인 보호자가 guardian_elder 로 해당 elder 와 연결됐는지 확인.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnershipService {

    private final ElderRepository elderRepository;
    private final GuardianElderRepository guardianElderRepository;

    /** elder 존재 + 소유권 검증 후 Elder 반환. 없으면 NOT_FOUND, 소유 아니면 FORBIDDEN. */
    public Elder verifyAndGetElder(Long userId, Long elderId) {
        Elder elder = elderRepository.findById(elderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!guardianElderRepository.existsByUserIdAndElderId(userId, elderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return elder;
    }

    /** 소유권만 검증(Elder 반환 불필요할 때). */
    public void verify(Long userId, Long elderId) {
        verifyAndGetElder(userId, elderId);
    }
}
