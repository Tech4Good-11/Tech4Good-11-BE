package com.example.demo.common;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Structured Outputs(strict) 용 JSON Schema 빌더.
 *
 * <p>strict 모드 규칙:
 * <ul>
 *   <li>모든 프로퍼티가 required 여야 한다</li>
 *   <li>object 는 additionalProperties=false 여야 한다</li>
 *   <li>선택값은 nullable union 으로 표현한다 (예: {"type":["integer","null"]})</li>
 * </ul>
 *
 * <p>스키마 생성은 OpenAI 호출과 무관한 순수 함수라 정적 유틸로 둔다
 * (클라이언트에 두면 단위 테스트에서 목킹되어 빈 스키마가 만들어진다).
 */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    /** {"type":"object","additionalProperties":false} 뼈대. */
    public static ObjectNode object(ObjectMapper om) {
        ObjectNode node = om.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        return node;
    }

    /** {"type":"<type>"} — 필수 값. */
    public static ObjectNode of(ObjectMapper om, String type) {
        ObjectNode node = om.createObjectNode();
        node.put("type", type);
        return node;
    }

    /** {"type":["<type>","null"]} — 값을 모를 수 있는 선택 값. */
    public static ObjectNode nullable(ObjectMapper om, String type) {
        ObjectNode node = om.createObjectNode();
        ArrayNode types = node.putArray("type");
        types.add(type);
        types.add("null");
        return node;
    }

    /**
     * 객체 배열 스키마.
     * propTypes 의 값은 {@link #of} / {@link #nullable} 로 만든 타입 노드다.
     */
    public static ObjectNode arrayOf(ObjectMapper om, List<String> required, Map<String, ObjectNode> propTypes) {
        ObjectNode array = om.createObjectNode();
        array.put("type", "array");
        ObjectNode item = object(om);
        ArrayNode req = item.putArray("required");
        required.forEach(req::add);
        ObjectNode props = item.putObject("properties");
        propTypes.forEach(props::set);
        array.set("items", item);
        return array;
    }
}
