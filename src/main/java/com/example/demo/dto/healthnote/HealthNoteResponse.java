package com.example.demo.dto.healthnote;

import com.example.demo.domain.ElderHealthNote;

import java.time.LocalDateTime;

public record HealthNoteResponse(
        Long elderId,
        String contentMd,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HealthNoteResponse from(ElderHealthNote note) {
        return new HealthNoteResponse(
                note.getElderId(),
                note.getContentMd(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
