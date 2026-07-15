package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;
import java.util.List;

/**
 * OpenAI Chat Completions 호출 래퍼.
 * - chat(): 텍스트 대화(챗봇)
 * - extractFromImage(): 이미지 입력(Vision) → 구조화 텍스트 추출
 * 요청/응답 JSON 은 Jackson(tools.jackson)으로 직접 구성/파싱한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final RestClient openAiRestClient;
    private final OpenAiProperties props;
    private final ObjectMapper objectMapper;

    /** 대화용 메시지 한 턴. role: system | user | assistant */
    public record ChatMessage(String role, String content) {
    }

    /** 텍스트 대화. system 프롬프트 + 메시지 목록 → assistant 응답 텍스트. */
    public String chat(String systemPrompt, List<ChatMessage> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model());
        ArrayNode msgs = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = msgs.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        for (ChatMessage m : messages) {
            ObjectNode n = msgs.addObject();
            n.put("role", m.role());
            n.put("content", m.content());
        }
        return callChatCompletions(body);
    }

    /**
     * 텍스트 대화 + <b>Structured Outputs</b>. 응답이 schema 를 반드시 따르도록 강제한다(타입 포함).
     * 모델이 스키마를 벗어난 답을 낼 수 없으므로 호출부에서 방어적 파싱이 필요 없다.
     *
     * @param schemaName 스키마 식별용 이름(영문/숫자/언더스코어)
     * @param schema     JSON Schema. strict 모드 규칙을 따라야 한다
     *                   (모든 프로퍼티가 required, additionalProperties=false, 선택값은 ["type","null"]).
     */
    public String chatWithSchema(String systemPrompt, List<ChatMessage> messages,
                                 String schemaName, ObjectNode schema) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model());
        ArrayNode msgs = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = msgs.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        for (ChatMessage m : messages) {
            ObjectNode n = msgs.addObject();
            n.put("role", m.role());
            n.put("content", m.content());
        }
        applyJsonSchema(body, schemaName, schema);
        return callChatCompletions(body);
    }

    /**
     * 이미지에서 정보 추출(Vision) + <b>Structured Outputs</b>.
     * instruction 은 "무엇을 뽑을지"만 담고, 출력 형태는 schema 가 강제한다.
     */
    public String extractFromImage(byte[] image, String mimeType, String instruction,
                                   String schemaName, ObjectNode schema) {
        String mime = (mimeType == null || mimeType.isBlank()) ? "image/jpeg" : mimeType;
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model());

        ArrayNode msgs = body.putArray("messages");
        ObjectNode user = msgs.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");

        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", instruction);

        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = imagePart.putObject("image_url");
        imageUrl.put("url", dataUrl);

        applyJsonSchema(body, schemaName, schema);
        return callChatCompletions(body);
    }

    /** response_format 에 strict JSON Schema 를 건다. */
    private void applyJsonSchema(ObjectNode body, String schemaName, ObjectNode schema) {
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);
    }


    // ---- 내부 ----

    private String model() {
        return (props.model() == null || props.model().isBlank()) ? "gpt-4o" : props.model();
    }

    private String callChatCompletions(ObjectNode body) {
        requireApiKey();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청 직렬화 실패");
        }

        String responseJson;
        try {
            responseJson = openAiRestClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("OpenAI API 오류 {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 호출 실패(" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            log.error("OpenAI 통신 오류", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 통신 오류");
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode message = root.path("choices").path(0).path("message");

            // Structured Outputs 사용 시, 모델이 스키마를 채울 수 없으면 content 대신 refusal 이 온다.
            JsonNode refusal = message.path("refusal");
            if (!refusal.isNull() && !refusal.isMissingNode() && !refusal.asString().isBlank()) {
                log.warn("OpenAI 응답 거부: {}", refusal.asString());
                throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 내용을 처리할 수 없습니다.");
            }

            String text = message.path("content").asString();
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 응답이 비어 있습니다.");
            }
            return text;
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 응답 파싱 실패");
        }
    }

    private void requireApiKey() {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "OPENAI_API_KEY 가 설정되지 않았습니다. 환경변수를 설정한 뒤 다시 시도하세요.");
        }
    }
}
