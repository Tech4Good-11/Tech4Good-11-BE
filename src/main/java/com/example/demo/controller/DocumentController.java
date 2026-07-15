package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.SessionUtil;
import com.example.demo.dto.document.DocumentIntakeResponse;
import com.example.demo.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Documents", description = "진단서/처방전 사진 업로드 → 해석 결과를 질병/복약으로 자동 등록. [사진 업로드 화면].")
@RestController
@RequestMapping("/api/elders/{elderId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final SessionUtil sessionUtil;

    @Operation(summary = "진단서/처방전 업로드·처리 (MOCK)",
            description = "**[진단서/처방전 사진 업로드 화면]**. `multipart/form-data` 로 `file`(이미지)과 "
                    + "`docType`(diagnosis=진단서 / prescription=처방전)을 전송한다. "
                    + "서버가 문서를 해석해 질병(elder_disease) 또는 복약(elder_medication)을 **자동 등록**하고, 처리 과정을 대화 로그로 남긴다. "
                    + "응답의 추출 결과 리스트를 화면에 보여준 뒤, 프론트는 대시보드/복약·질병 목록을 새로고침하면 된다. "
                    + "\n\n⚠️ **현재 MOCK**: 실제 OCR/LLM 없이 docType 별 고정 예시 결과를 반환한다(단, DB 에는 실제로 저장됨).")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentIntakeResponse> upload(@PathVariable Long elderId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam("docType") String docType,
                                                      HttpSession session) {
        Long userId = sessionUtil.currentUserId(session);
        return ApiResponse.success(documentService.process(userId, elderId, file, docType));
    }
}
