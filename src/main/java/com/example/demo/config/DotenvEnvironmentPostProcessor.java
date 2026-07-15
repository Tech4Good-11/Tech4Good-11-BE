package com.example.demo.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 프로젝트 루트의 {@code .env} 파일을 Spring 환경에 주입한다.
 *
 * <p>Spring 은 기본적으로 {@code .env} 를 읽지 않는다(Node 진영 관례). 이 클래스가 없으면
 * {@code application.properties} 의 {@code ${OPENAI_API_KEY}} 가 비어 있어 AI 기능이 동작하지 않고,
 * 팀원마다 셸에서 {@code export} 를 해야 한다.
 *
 * <p>우선순위: 실제 OS 환경변수가 {@code .env} 보다 우선하도록 가장 낮은 순위로 추가한다
 * (CI/배포 환경에서 주입한 값이 로컬 {@code .env} 에 덮이면 안 된다).
 *
 * <p>{@code .env} 가 없어도 조용히 넘어간다(운영 환경에는 파일이 없는 게 정상).
 *
 * <p>등록: {@code src/main/resources/META-INF/spring.factories}
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()   // .env 가 없으면 무시
                .ignoreIfMalformed()
                .load();

        Map<String, Object> values = new HashMap<>();
        dotenv.entries().forEach(entry -> values.put(entry.getKey(), entry.getValue()));

        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        }
    }
}
