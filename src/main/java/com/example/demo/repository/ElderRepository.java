package com.example.demo.repository;

import com.example.demo.domain.Elder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElderRepository extends JpaRepository<Elder, Long> {
}
