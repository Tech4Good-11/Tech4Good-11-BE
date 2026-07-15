package com.example.demo.repository;

import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.enums.ConversationPurpose;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
    Page<AgentConversation> findByElderId(Long elderId, Pageable pageable);
    Page<AgentConversation> findByElderIdAndPurpose(Long elderId, ConversationPurpose purpose, Pageable pageable);
    List<AgentConversation> findTop5ByElderIdOrderByCreatedAtDesc(Long elderId);
}
