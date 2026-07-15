package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.elder.ElderCreateRequest;
import com.example.demo.dto.elder.ElderResponse;
import com.example.demo.dto.elder.ElderSummaryResponse;
import com.example.demo.dto.elder.ElderUpdateRequest;
import com.example.demo.service.ElderService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Elders", description = "시니어(어르신) 관리. 홈 화면의 어르신 목록·상세 프로필의 기반 데이터.")
@RestController
@RequestMapping("/api/elders")
@RequiredArgsConstructor
public class ElderController {

    private final ElderService elderService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "시니어 등록 (등록과 동시에 보호관계 생성)",
            description = "관리할 어르신을 등록한다. **[어르신 추가 화면]** 에서 사용. "
                    + "등록과 동시에 로그인 보호자와의 보호관계(guardian_elder)가 생성되어 바로 '내 어르신 목록'에 나타난다. "
                    + "이후 이 어르신의 질병/복약/대시보드 API는 반환된 elderId 로 호출한다.")
    @PostMapping
    public ApiResponse<ElderResponse> create(@RequestBody ElderCreateRequest request, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(elderService.create(userId, request));
    }

    @Operation(summary = "내가 보호하는 시니어 목록",
            description = "로그인 보호자가 돌보는 어르신 카드 리스트. **[홈 / 어르신 선택 화면]** 에 렌더. "
                    + "각 항목에 활성 복약 수·질병 수·마지막 안부 시각 요약이 포함되어 카드 뱃지로 바로 표시할 수 있다.")
    @GetMapping
    public ApiResponse<List<ElderSummaryResponse>> list(HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(elderService.listMyElders(userId));
    }

    @Operation(summary = "시니어 상세 (소유권 검증)",
            description = "어르신 기본 정보(이름/생년월일/성별/연락처). **[어르신 프로필·정보 수정 화면]** 진입 시 사용. "
                    + "내가 돌보지 않는 어르신이면 403(FORBIDDEN).")
    @GetMapping("/{elderId}")
    public ApiResponse<ElderResponse> detail(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(elderService.getDetail(userId, elderId));
    }

    @Operation(summary = "시니어 정보 수정 (소유권 검증)",
            description = "어르신 기본 정보를 수정한다. **[어르신 정보 수정 화면]** 의 저장 버튼. "
                    + "보호관계(relationship)는 이 API로 바꾸지 않는다.")
    @PutMapping("/{elderId}")
    public ApiResponse<ElderResponse> update(@PathVariable Long elderId,
                                             @RequestBody ElderUpdateRequest request,
                                             HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(elderService.update(userId, elderId, request));
    }

    @Operation(summary = "시니어 삭제 (연관 데이터 CASCADE 삭제)",
            description = "어르신과 그에 딸린 모든 데이터(질병·복약·대화·건강노트·보호관계)를 함께 삭제한다. "
                    + "**되돌릴 수 없으므로** 프론트에서 확인 다이얼로그를 띄운 뒤 호출할 것.")
    @DeleteMapping("/{elderId}")
    public ApiResponse<Void> delete(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        elderService.delete(userId, elderId);
        return ApiResponse.<Void>success("삭제되었습니다.", null);
    }
}
