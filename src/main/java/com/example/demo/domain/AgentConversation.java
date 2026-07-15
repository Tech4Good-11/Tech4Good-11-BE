package com.example.demo.domain;

import com.example.demo.domain.enums.ConversationPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 에이전트 대화 로그. 테이블: agent_conversation
 * transcript 는 JSON 컬럼이며, 직렬화된 JSON 문자열(String)로 매핑한다.
 */
@Entity
@Table(name = "agent_conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationPurpose purpose;

    /** JSON blob. MySQL JSON 컬럼에 유효한 JSON 문자열로 저장. */
    @Column(nullable = false, columnDefinition = "json")
    private String transcript;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AgentConversation(Long elderId, ConversationPurpose purpose, String transcript) {
        this.elderId = elderId;
        this.purpose = purpose;
        this.transcript = transcript;
    }
}
