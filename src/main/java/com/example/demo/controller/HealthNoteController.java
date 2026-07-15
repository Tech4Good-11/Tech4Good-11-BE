package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.healthnote.HealthNoteResponse;
import com.example.demo.dto.healthnote.HealthNoteUpdateRequest;
import com.example.demo.service.HealthNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "HealthNote", description = "어르신 건강 요약(마크다운, 어르신당 1개). 대시보드 요약 카드/노트 화면에 렌더.")
@RestController
@RequestMapping("/api/elders/{elderId}/health-note")
@RequiredArgsConstructor
public class HealthNoteController {

    private final HealthNoteService healthNoteService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "건강 컨텍스트 조회 (없으면 null)",
            description = "어르신 건강 요약을 **마크다운 문자열(contentMd)** 로 반환한다. 프론트는 마크다운 렌더러로 표시. "
                    + "**[대시보드 요약 카드 / 건강노트 상세]** 에 사용. 아직 노트가 없으면 data=null → 빈 상태 UI 표시.")
    @GetMapping
    public ApiResponse<HealthNoteResponse> get(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(healthNoteService.get(userId, elderId));
    }

    @Operation(summary = "건강 컨텍스트 갱신 (없으면 생성, upsert)",
            description = "마크다운 노트를 저장한다(없으면 생성). **[건강노트 편집 화면]의 저장 버튼**. "
                    + "보호자 수동 편집 또는 서버측 LLM 갱신이 이 API를 쓴다.")
    @PutMapping
    public ApiResponse<HealthNoteResponse> upsert(@PathVariable Long elderId,
                                                  @RequestBody HealthNoteUpdateRequest request,
                                                  HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(healthNoteService.upsert(userId, elderId, request));
    }
}
