package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 상단에 표시되는 문서 메타 정보. http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI demoOpenAPI() {
        String description = """
                온기(ongi) - 시니어 건강관리 앱 백엔드 API.
                자녀(보호자)가 에이전트를 통해 어르신의 건강을 관리한다.

                ## 프론트 연동 공통 규약
                - **인증: 세션 기반**. `POST /api/auth/login` 성공 시 세션 쿠키(SESSION)가 발급된다.
                  이후 모든 인증 API 요청에 쿠키를 함께 보낼 것 (axios `withCredentials:true` / fetch `credentials:'include'`).
                  앱 진입 시 `GET /api/auth/me` 로 로그인 상태를 확인하고, 401 이면 로그인 화면으로 보낸다.
                - **공통 응답 포맷**: 모든 응답은 `{ success, message, data }` 로 감싸진다.
                  성공 시 실제 페이로드는 `data` 안에 있고, 실패 시 `success:false` + `message`(에러 사유), `data:null`.
                - **에러 코드(HTTP)**: 400 잘못된 입력 / 401 로그인 필요 / 403 권한 없음(내가 돌보지 않는 어르신) / 404 없음 / 409 중복 / 500 서버오류.
                - **소유권**: `/api/elders/{elderId}/...` 는 로그인 보호자가 그 어르신을 돌보는 경우에만 접근 가능(아니면 403).
                - **화면 시작점**: 대부분의 상세 화면은 `GET /api/elders/{elderId}/dashboard` 한 번으로 구성할 수 있다.
                - 요약에 **(MOCK)** 표기가 있는 엔드포인트는 아직 실제 지능(OCR/LLM) 없이 예시 결과를 반환한다(단, DB 저장은 실제로 동작).
                """;
        return new OpenAPI()
                .info(new Info()
                        .title("온기(ongi) API")
                        .description(description)
                        .version("v0.0.1"));
    }
}
