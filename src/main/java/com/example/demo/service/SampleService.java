package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.Sample;
import com.example.demo.dto.SampleRequest;
import com.example.demo.dto.SampleResponse;
import com.example.demo.repository.SampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SampleService {

    private final SampleRepository sampleRepository;

    @Transactional
    public SampleResponse create(SampleRequest request) {
        Sample sample = Sample.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return SampleResponse.from(sampleRepository.save(sample));
    }

    public List<SampleResponse> findAll() {
        return sampleRepository.findAll().stream()
                .map(SampleResponse::from)
                .toList();
    }

    public SampleResponse findById(Long id) {
        Sample sample = sampleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return SampleResponse.from(sample);
    }

    @Transactional
    public SampleResponse update(Long id, SampleRequest request) {
        Sample sample = sampleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        sample.update(request.name(), request.description());
        return SampleResponse.from(sample);
    }

    @Transactional
    public void delete(Long id) {
        if (!sampleRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        sampleRepository.deleteById(id);
    }
}
