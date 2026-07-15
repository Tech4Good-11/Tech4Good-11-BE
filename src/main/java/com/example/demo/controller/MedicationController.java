package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.dto.medication.MedicationRequest;
import com.example.demo.dto.medication.MedicationResponse;
import com.example.demo.service.MedicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Medications", description = "어르신 복약 실데이터. 건강정보 탭의 복약 리스트/편집. atc_code 는 리마인드 매칭 키.")
@RestController
@RequestMapping("/api/elders/{elderId}/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "복약 목록 (status 필터 선택)",
            description = "어르신 복약 목록. **[건강정보 탭 > 복약]** 에 렌더. intervalHours(복용 간격)로 복약 스케줄 UI 구성. "
                    + "`?status=active|stopped|completed` 로 복용중만 필터링 가능.")
    @GetMapping
    public ApiResponse<List<MedicationResponse>> list(@PathVariable Long elderId,
                                                      @RequestParam(required = false) MedicationStatus status,
                                                      HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(medicationService.list(userId, elderId, status));
    }

    @Operation(summary = "복약 추가 (수동)",
            description = "복약을 직접 추가한다. **[복약 추가 폼]**. (처방전 사진으로 자동 추가하려면 Documents API 사용) "
                    + "atc_code 를 넣으면 해당 코드에 걸린 복약 리마인드 규칙이 자동 매칭된다.")
    @PostMapping
    public ApiResponse<MedicationResponse> create(@PathVariable Long elderId,
                                                  @RequestBody MedicationRequest request,
                                                  HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(medicationService.create(userId, elderId, request));
    }

    @Operation(summary = "복약 수정",
            description = "복약 정보/상태를 수정한다. 예: 복용 중단 시 status 를 stopped 로 변경. **[복약 카드 편집]**.")
    @PutMapping("/{medicationId}")
    public ApiResponse<MedicationResponse> update(@PathVariable Long elderId,
                                                  @PathVariable Long medicationId,
                                                  @RequestBody MedicationRequest request,
                                                  HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(medicationService.update(userId, elderId, medicationId, request));
    }

    @Operation(summary = "복약 삭제",
            description = "복약 항목을 삭제한다. **[복약 카드의 삭제 버튼]**. 잘못 등록된 항목 제거용.")
    @DeleteMapping("/{medicationId}")
    public ApiResponse<Void> delete(@PathVariable Long elderId,
                                    @PathVariable Long medicationId,
                                    HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        medicationService.delete(userId, elderId, medicationId);
        return ApiResponse.<Void>success("삭제되었습니다.", null);
    }
}
