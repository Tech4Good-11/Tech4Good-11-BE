package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.dto.checkin.CheckinSubmitRequest;
import com.example.demo.dto.checkin.CheckinSubmitResponse;
import com.example.demo.dto.checkin.CheckinTodayResponse;
import com.example.demo.dto.reminder.ElderReminderResponse;
import com.example.demo.repository.AgentConversationRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckinService {

    private final ReminderService reminderService;
    private final OwnershipService ownershipService;
    private final AgentConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    /** 오늘의 문진 항목 = yes_no 응답이 필요한 리마인드 규칙. */
    public List<CheckinTodayResponse> today(Long userId, Long elderId) {
        Elder elder = ownershipService.verifyAndGetElder(userId, elderId);
        List<ElderReminderResponse> reminders = reminderService.matchRules(elderId, elder.getName());
        return reminders.stream()
                .filter(r -> r.expectedResponse() == ExpectedResponse.yes_no)
                .map(r -> new CheckinTodayResponse(
                        r.ruleCode(),
                        r.message(),
                        r.expectedResponse(),
                        r.times()
                ))
                .toList();
    }

    /** 문진 응답 제출 → daily_checkin 대화로 저장. */
    @Transactional
    public CheckinSubmitResponse submit(Long userId, Long elderId, CheckinSubmitRequest request) {
        ownershipService.verify(userId, elderId);
        if (request.answers() == null || request.answers().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "answers 는 필수입니다.");
        }
        // transcript 를 JSON 배열로 구성: [{role:agent, ruleCode}, {role:elder, answer}]
        ArrayNode arr = objectMapper.createArrayNode();
        for (CheckinSubmitRequest.Answer a : request.answers()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", "elder");
            node.put("ruleCode", a.ruleCode());
            node.put("answer", a.answer());
            arr.add(node);
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(arr);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        AgentConversation conv = conversationRepository.save(AgentConversation.builder()
                .elderId(elderId)
                .purpose(ConversationPurpose.daily_checkin)
                .transcript(json)
                .build());
        // MOCK: 실제로는 응답 결과로 elder_health_note 를 LLM 이 갱신하나, 여기서는 저장만 수행.
        return new CheckinSubmitResponse(conv.getId(), conv.getCreatedAt());
    }
}
