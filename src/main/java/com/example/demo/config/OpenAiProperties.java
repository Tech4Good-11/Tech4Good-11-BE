package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 연동 설정. application.properties 의 openai.* 값이 바인딩된다.
 * apiKey 는 환경변수 OPENAI_API_KEY 로 주입한다 (openai.api-key=${OPENAI_API_KEY:}).
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        String baseUrl
) {
}
