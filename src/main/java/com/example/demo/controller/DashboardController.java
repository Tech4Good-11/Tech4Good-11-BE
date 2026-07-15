package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.dashboard.DashboardResponse;
import com.example.demo.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "어르신 상세 대시보드 화면을 한 번에 구성하는 통합 조회.")
@RestController
@RequestMapping("/api/elders/{elderId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "통합 대시보드 (건강노트+질병+복약+오늘 리마인드+최근 안부)",
            description = "**[어르신 상세 대시보드 화면]의 메인 API.** 화면 진입 시 이 한 번의 호출로 "
                    + "① 건강노트(마크다운) ② 질병 목록 ③ 복약 목록 ④ 오늘의 리마인드 ⑤ 최근 안부 기록을 모두 받아 화면을 구성한다. "
                    + "개별 탭(질병/복약 등)에서 상세 조작이 필요할 때만 각 리소스 API를 별도로 호출하면 된다.")
    @GetMapping
    public ApiResponse<DashboardResponse> dashboard(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(dashboardService.getDashboard(userId, elderId));
    }
}
