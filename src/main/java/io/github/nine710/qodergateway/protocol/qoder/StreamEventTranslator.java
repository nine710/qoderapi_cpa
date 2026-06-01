package io.github.nine710.qodergateway.protocol.qoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.nine710.qodergateway.support.model.StreamDelta;
import org.springframework.stereotype.Component;

@Component
public class StreamEventTranslator {

    private final ObjectMapper objectMapper;

    public StreamEventTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StreamDelta translate(String dataLine) {
        try {
            JsonNode wrapper = objectMapper.readTree(dataLine);
            String body = wrapper.path("body").asText("");
            if (body.isBlank()) {
                return StreamDelta.empty();
            }

            JsonNode inner = objectMapper.readTree(body);
            for (JsonNode choice : inner.path("choices")) {
                JsonNode delta = choice.path("delta");
                String role = delta.path("role").asText("");
                String content = delta.path("content").asText("");
                ArrayNode toolCalls = null;
                if (delta.path("tool_calls").isArray() && delta.path("tool_calls").size() > 0) {
                    toolCalls = (ArrayNode) delta.path("tool_calls").deepCopy();
                }
                if (!role.isBlank() || !content.isBlank() || toolCalls != null) {
                    return new StreamDelta(role, content, toolCalls);
                }
            }
        } catch (Exception ignore) {
        }
        return StreamDelta.empty();
    }
}
