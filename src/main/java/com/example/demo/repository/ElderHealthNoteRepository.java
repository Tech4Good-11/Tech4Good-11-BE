package com.example.demo.repository;

import com.example.demo.domain.ElderHealthNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ElderHealthNoteRepository extends JpaRepository<ElderHealthNote, Long> {
    Optional<ElderHealthNote> findByElderId(Long elderId);
}
