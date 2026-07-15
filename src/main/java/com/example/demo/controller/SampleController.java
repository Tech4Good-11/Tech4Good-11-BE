package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.SampleRequest;
import com.example.demo.dto.SampleResponse;
import com.example.demo.service.SampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Sample", description = "샘플 CRUD API (복사해서 실제 API 작성)")
@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @Operation(summary = "샘플 생성")
    @PostMapping
    public ApiResponse<SampleResponse> create(@RequestBody SampleRequest request) {
        return ApiResponse.success(sampleService.create(request));
    }

    @Operation(summary = "샘플 전체 조회")
    @GetMapping
    public ApiResponse<List<SampleResponse>> findAll() {
        return ApiResponse.success(sampleService.findAll());
    }

    @Operation(summary = "샘플 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<SampleResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(sampleService.findById(id));
    }

    @Operation(summary = "샘플 수정")
    @PutMapping("/{id}")
    public ApiResponse<SampleResponse> update(@PathVariable Long id, @RequestBody SampleRequest request) {
        return ApiResponse.success(sampleService.update(id, request));
    }

    @Operation(summary = "샘플 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sampleService.delete(id);
        return ApiResponse.<Void>success("삭제되었습니다.", null);
    }
}
