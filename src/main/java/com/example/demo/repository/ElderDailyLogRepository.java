package com.example.demo.repository;

import com.example.demo.domain.ElderDailyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ElderDailyLogRepository extends JpaRepository<ElderDailyLog, Long> {
    Optional<ElderDailyLog> findByElderIdAndLogDate(Long elderId, LocalDate logDate);
}
