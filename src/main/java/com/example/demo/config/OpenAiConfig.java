package com.example.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * OpenAI 호출용 RestClient 빈. base-url 과 인증 헤더(Authorization: Bearer)를 미리 설정한다.
 * LLM 응답이 느릴 수 있어 read timeout 을 넉넉히 준다.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    @Bean
    public RestClient openAiRestClient(OpenAiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));

        String baseUrl = (props.baseUrl() == null || props.baseUrl().isBlank())
                ? "https://api.openai.com/v1"
                : props.baseUrl();
        String apiKey = props.apiKey() == null ? "" : props.apiKey();

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}
