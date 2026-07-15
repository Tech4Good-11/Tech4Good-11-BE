package com.example.demo.repository;

import com.example.demo.domain.ElderDailyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ElderDailyLogRepository extends JpaRepository<ElderDailyLog, Long> {
    Optional<ElderDailyLog> findByElderIdAndLogDate(Long elderId, LocalDate logDate);

    /** 자녀 상담 시 최근 생활 추이를 근거로 제시하기 위한 조회. */
    List<ElderDailyLog> findTop7ByElderIdOrderByLogDateDesc(Long elderId);
}
