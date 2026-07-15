package com.example.demo.repository;

import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.enums.DiseaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElderDiseaseRepository extends JpaRepository<ElderDisease, Long> {
    List<ElderDisease> findByElderId(Long elderId);
    List<ElderDisease> findByElderIdAndStatus(Long elderId, DiseaseStatus status);
    long countByElderIdAndStatusNot(Long elderId, DiseaseStatus status);
}
