package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.guardian.GuardianAddRequest;
import com.example.demo.dto.guardian.GuardianResponse;
import com.example.demo.service.GuardianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Guardians", description = "공동 보호자(M:N). 한 어르신을 여러 가족이 함께 돌볼 때. [가족 공유 화면].")
@RestController
@RequestMapping("/api/elders/{elderId}/guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "이 시니어의 보호자 목록",
            description = "이 어르신을 함께 돌보는 보호자(가족) 목록. **[가족 공유/구성원 화면]** 에 렌더.")
    @GetMapping
    public ApiResponse<List<GuardianResponse>> list(@PathVariable Long elderId, HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(guardianService.listGuardians(userId, elderId));
    }

    @Operation(summary = "공동 보호자 추가",
            description = "다른 가족을 **이메일로 지정**해 이 어르신의 공동 보호자로 추가한다(이미 가입된 계정이어야 함). "
                    + "**[가족 초대]** 버튼. 이미 보호자면 409(CONFLICT).")
    @PostMapping
    public ApiResponse<GuardianResponse> add(@PathVariable Long elderId,
                                             @RequestBody GuardianAddRequest request,
                                             HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(guardianService.addGuardian(userId, elderId, request));
    }

    @Operation(summary = "보호관계 해제",
            description = "지정한 보호자(userId)를 이 어르신에서 제외한다. **[가족 구성원 관리]** 의 내보내기 버튼. "
                    + "어르신 데이터 자체는 삭제되지 않는다.")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> remove(@PathVariable Long elderId,
                                    @PathVariable("userId") Long targetUserId,
                                    HttpSession session) {
        Long currentUserId = sessionUtil.currentUserId(session);
        guardianService.removeGuardian(currentUserId, elderId, targetUserId);
        return ApiResponse.<Void>success("보호관계가 해제되었습니다.", null);
    }
}
