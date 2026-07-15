package com.example.demo.repository;

import com.example.demo.domain.GuardianElder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuardianElderRepository extends JpaRepository<GuardianElder, Long> {
    boolean existsByUserIdAndElderId(Long userId, Long elderId);
    List<GuardianElder> findByUserId(Long userId);
    List<GuardianElder> findByElderId(Long elderId);
    Optional<GuardianElder> findByUserIdAndElderId(Long userId, Long elderId);
}
