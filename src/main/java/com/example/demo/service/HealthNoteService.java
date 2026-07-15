package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.ElderHealthNote;
import com.example.demo.dto.healthnote.HealthNoteResponse;
import com.example.demo.dto.healthnote.HealthNoteUpdateRequest;
import com.example.demo.repository.ElderHealthNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthNoteService {

    private final ElderHealthNoteRepository healthNoteRepository;
    private final OwnershipService ownershipService;

    /** 없으면 null 반환. */
    public HealthNoteResponse get(Long userId, Long elderId) {
        ownershipService.verify(userId, elderId);
        return healthNoteRepository.findByElderId(elderId)
                .map(HealthNoteResponse::from)
                .orElse(null);
    }

    /** upsert: 없으면 생성, 있으면 갱신. */
    @Transactional
    public HealthNoteResponse upsert(Long userId, Long elderId, HealthNoteUpdateRequest request) {
        ownershipService.verify(userId, elderId);
        if (request.contentMd() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "contentMd 는 필수입니다.");
        }
        ElderHealthNote note = healthNoteRepository.findByElderId(elderId).orElse(null);
        if (note == null) {
            note = healthNoteRepository.save(ElderHealthNote.builder()
                    .elderId(elderId)
                    .contentMd(request.contentMd())
                    .build());
        } else {
            note.update(request.contentMd());
        }
        return HealthNoteResponse.from(note);
    }
}
