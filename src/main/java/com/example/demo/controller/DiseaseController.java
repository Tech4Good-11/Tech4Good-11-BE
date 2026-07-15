package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.dto.disease.DiseaseRequest;
import com.example.demo.dto.disease.DiseaseResponse;
import com.example.demo.service.DiseaseService;
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

@Tag(name = "Diseases", description = "어르신 질병 실데이터. 건강정보 탭의 질병 리스트/편집. icd_code 는 리마인드 매칭 키.")
@RestController
@RequestMapping("/api/elders/{elderId}/diseases")
@RequiredArgsConstructor
public class DiseaseController {

    private final DiseaseService diseaseService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "질병 목록 (status 필터 선택)",
            description = "어르신 질병 목록. **[건강정보 탭 > 질병]** 에 렌더. "
                    + "`?status=active|managed|resolved` 로 진행중만 필터링 가능(예: 대시보드엔 active/managed 만 노출).")
    @GetMapping
    public ApiResponse<List<DiseaseResponse>> list(@PathVariable Long elderId,
                                                   @RequestParam(required = false) DiseaseStatus status,
                                                   HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(diseaseService.list(userId, elderId, status));
    }

    @Operation(summary = "질병 추가 (수동)",
            description = "질병을 직접 추가한다. **[질병 추가 폼]**. (진단서 사진으로 자동 추가하려면 Documents API 사용) "
                    + "icd_code 를 넣으면 해당 코드에 걸린 리마인드 규칙이 자동으로 매칭된다.")
    @PostMapping
    public ApiResponse<DiseaseResponse> create(@PathVariable Long elderId,
                                               @RequestBody DiseaseRequest request,
                                               HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(diseaseService.create(userId, elderId, request));
    }

    @Operation(summary = "질병 수정",
            description = "질병 정보/상태를 수정한다. 예: 완치 시 status 를 resolved 로 변경. **[질병 카드 편집]**.")
    @PutMapping("/{diseaseId}")
    public ApiResponse<DiseaseResponse> update(@PathVariable Long elderId,
                                               @PathVariable Long diseaseId,
                                               @RequestBody DiseaseRequest request,
                                               HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(diseaseService.update(userId, elderId, diseaseId, request));
    }

    @Operation(summary = "질병 삭제",
            description = "질병 항목을 삭제한다. **[질병 카드의 삭제 버튼]**. 잘못 등록된 항목 제거용.")
    @DeleteMapping("/{diseaseId}")
    public ApiResponse<Void> delete(@PathVariable Long elderId,
                                    @PathVariable Long diseaseId,
                                    HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        diseaseService.delete(userId, elderId, diseaseId);
        return ApiResponse.<Void>success("삭제되었습니다.", null);
    }
}
