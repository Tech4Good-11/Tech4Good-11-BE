package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.dto.conversation.ConversationCreateRequest;
import com.example.demo.dto.conversation.ConversationDetailResponse;
import com.example.demo.dto.conversation.ConversationSummaryResponse;
import com.example.demo.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Conversations", description = "에이전트-어르신 대화 기록. 저장은 주로 에이전트 클라이언트가, 조회는 [대화 히스토리 화면]이 사용.")
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "대화 기록 저장 (transcript=JSON)",
            description = "에이전트-어르신 대화 한 세션을 통째로 저장한다. `transcript` 는 발화 배열(JSON). "
                    + "**주로 에이전트/음성 클라이언트가** 대화 종료 시 호출한다(일반 보호자 화면용은 아님). "
                    + "purpose: daily_checkin(안부문진) / document_intake(문서처리) / free(자유대화).")
    @PostMapping("/api/elders/{elderId}/conversations")
    public ApiResponse<ConversationSummaryResponse> create(@PathVariable Long elderId,
                                                           @RequestBody ConversationCreateRequest request,
                                                           HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(conversationService.create(userId, elderId, request));
    }

    @Operation(summary = "대화 목록 (transcript 제외, 페이지네이션)",
            description = "어르신의 대화 이력 요약 목록. **[대화 히스토리 화면]** 에 렌더(내용 전문은 제외, 상세에서 로드). "
                    + "`?purpose=` 로 종류 필터, `?page=&size=` 로 페이지네이션.")
    @GetMapping("/api/elders/{elderId}/conversations")
    public ApiResponse<List<ConversationSummaryResponse>> list(@PathVariable Long elderId,
                                                               @RequestParam(required = false) ConversationPurpose purpose,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        Page<ConversationSummaryResponse> result = conversationService.list(userId, elderId, purpose, page, size);
        return ApiResponse.success(result.getContent());
    }

    @Operation(summary = "대화 상세 (transcript 포함, 소유권 검증)",
            description = "특정 대화의 전체 발화(transcript)를 반환한다. **[대화 상세 보기]** 에서 말풍선 렌더에 사용. "
                    + "대화가 속한 어르신을 내가 돌보지 않으면 403.")
    @GetMapping("/api/conversations/{conversationId}")
    public ApiResponse<ConversationDetailResponse> detail(@PathVariable Long conversationId,
                                                          HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(conversationService.getDetail(userId, conversationId));
    }
}
