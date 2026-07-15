package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.dailylog.DailyLogResponse;
import com.example.demo.dto.dailylog.DailyLogUpdateRequest;
import com.example.demo.dto.dailylog.MedicationIntakeRequest;
import com.example.demo.dto.dailylog.MedicationIntakeResponse;
import com.example.demo.service.DailyLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "DailyLog", description = "하루 생활 로그(수면/운동/AI요약/체크리스트)와 복약 여부. 대시보드 지표의 원천.")
@RestController
@RequestMapping("/api/elders/{elderId}")
@RequiredArgsConstructor
public class DailyLogController {

    private final DailyLogService dailyLogService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "하루 생활 로그 조회",
            description = "지정 날짜(기본 오늘)의 수면시간·운동량·AI요약·체크리스트 응답을 반환한다. "
                    + "값이 **null 이면 아직 대화/입력으로 알아내지 못한 것**이므로 프론트는 '기록 없음'을 표시한다. "
                    + "대시보드에도 같은 값이 `dailyLog` 로 포함되므로, 별도 화면에서만 필요하면 이 API 를 쓴다.")
    @GetMapping("/daily-log")
    public ApiResponse<DailyLogResponse> get(@PathVariable Long elderId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(dailyLogService.get(userId, elderId, date));
    }

    @Operation(summary = "하루 생활 로그 수동 저장/수정",
            description = "보호자가 수면시간·운동량·요약을 직접 입력/수정한다(부분 수정: null 필드는 변경 안 함). "
                    + "`logDate` 생략 시 오늘. **[대시보드 직접 입력 UI]** 용. "
                    + "보통은 대화에서 자동 추출되므로 보정이 필요할 때만 사용한다.")
    @PutMapping("/daily-log")
    public ApiResponse<DailyLogResponse> upsert(@PathVariable Long elderId,
                                                @RequestBody DailyLogUpdateRequest request,
                                                HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(dailyLogService.upsert(userId, elderId, request));
    }

    @Operation(summary = "대화에서 지표 추출 (OpenAI)",
            description = "저장된 대화를 LLM 으로 분석해 **수면시간·운동량·AI요약·복약여부·질병 현재상황**을 추출·저장한다. "
                    + "`conversationId` 를 생략하면 가장 최근 대화를 사용한다. "
                    + "챗봇 대화를 `save=true` 로 저장하면 자동 추출되므로, 이 API 는 **재분석/보정용**이다. "
                    + "\n\n⚠️ `OPENAI_API_KEY` 필요.")
    @PostMapping("/daily-log/extract")
    public ApiResponse<DailyLogResponse> extract(@PathVariable Long elderId,
                                                 @RequestParam(required = false) Long conversationId,
                                                 HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(dailyLogService.extractFromConversation(userId, elderId, conversationId));
    }

    @Operation(summary = "오늘치 복용한 약 조회",
            description = "활성 약 전체와 해당 일자 복용 여부를 반환한다. `taken=null` 은 아직 확인 전(미확인). "
                    + "대시보드의 `todayMedications` 와 동일한 값.")
    @GetMapping("/medication-intake")
    public ApiResponse<List<MedicationIntakeResponse>> intakes(@PathVariable Long elderId,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(dailyLogService.getIntakes(userId, elderId, date));
    }

    @Operation(summary = "복약 체크 (체크박스 연동)",
            description = "**[대시보드 체크리스트의 약 복용 체크박스]** 를 누를 때 호출한다. "
                    + "`{medicationId, taken}` 전송(`intakeDate` 생략 시 오늘). 같은 약/같은 날은 덮어쓴다. "
                    + "저장하면 대시보드의 `todayMedications` 와 건강점수에 즉시 반영된다.")
    @PostMapping("/medication-intake")
    public ApiResponse<MedicationIntakeResponse> recordIntake(@PathVariable Long elderId,
                                                              @RequestBody MedicationIntakeRequest request,
                                                              HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(dailyLogService.recordIntake(userId, elderId, request));
    }
}
