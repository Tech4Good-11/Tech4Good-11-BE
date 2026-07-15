package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.dto.conversation.ConversationCreateRequest;
import com.example.demo.dto.conversation.ConversationDetailResponse;
import com.example.demo.dto.conversation.ConversationSummaryResponse;
import com.example.demo.repository.AgentConversationRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private final AgentConversationRepository conversationRepository;
    private final OwnershipService ownershipService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ConversationSummaryResponse create(Long userId, Long elderId, ConversationCreateRequest request) {
        ownershipService.verify(userId, elderId);
        if (request.transcript() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "transcript 는 필수입니다.");
        }
        String json = writeJson(request.transcript());
        AgentConversation conv = conversationRepository.save(AgentConversation.builder()
                .elderId(elderId)
                .purpose(request.purpose() != null ? request.purpose() : ConversationPurpose.free)
                .transcript(json)
                .build());
        return ConversationSummaryResponse.from(conv);
    }

    public Page<ConversationSummaryResponse> list(Long userId, Long elderId, ConversationPurpose purpose,
                                                  int page, int size) {
        ownershipService.verify(userId, elderId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentConversation> result = (purpose == null)
                ? conversationRepository.findByElderId(elderId, pageable)
                : conversationRepository.findByElderIdAndPurpose(elderId, purpose, pageable);
        return result.map(ConversationSummaryResponse::from);
    }

    public ConversationDetailResponse getDetail(Long userId, Long conversationId) {
        AgentConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 소유권: 이 대화의 elder 를 보호하는지
        ownershipService.verify(userId, conv.getElderId());
        return ConversationDetailResponse.of(conv, readJson(conv.getTranscript()));
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "transcript JSON 이 올바르지 않습니다.");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
