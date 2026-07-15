package com.example.demo.repository;

import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.enums.MedicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElderMedicationRepository extends JpaRepository<ElderMedication, Long> {
    List<ElderMedication> findByElderId(Long elderId);
    List<ElderMedication> findByElderIdAndStatus(Long elderId, MedicationStatus status);
    long countByElderIdAndStatus(Long elderId, MedicationStatus status);
}
