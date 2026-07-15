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
     * 이미지에서 정보 추출(Vision). instruction 으로 원하는 출력(JSON 등)을 지시한다.
     * response_format=json_object 로 순수 JSON 응답을 유도한다.
     */
    public String extractFromImage(byte[] image, String mimeType, String instruction) {
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

        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");

        return callChatCompletions(body);
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
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String text = contentNode.asString();
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
