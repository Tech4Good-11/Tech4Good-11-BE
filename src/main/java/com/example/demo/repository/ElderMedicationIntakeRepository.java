package com.example.demo.repository;

import com.example.demo.domain.ElderMedicationIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ElderMedicationIntakeRepository extends JpaRepository<ElderMedicationIntake, Long> {
    List<ElderMedicationIntake> findByElderIdAndIntakeDate(Long elderId, LocalDate intakeDate);
    Optional<ElderMedicationIntake> findByMedicationIdAndIntakeDate(Long medicationId, LocalDate intakeDate);
}
