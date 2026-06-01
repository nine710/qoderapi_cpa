package io.github.nine710.qodergateway.protocol.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nine710.qodergateway.support.model.OpenAiChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatRequestNormalizer normalizer = new ChatRequestNormalizer();

    @Test
    void normalizeTracksLatestUserPromptAndPreservesMessageOrder() {
        JsonNode system = message("system", objectMapper.valueToTree("s"));
        JsonNode user1 = message("user", objectMapper.valueToTree("hello"));
        JsonNode assistant = message("assistant", objectMapper.valueToTree("hi"));
        JsonNode user2 = message("user", objectMapper.valueToTree("latest"));
        OpenAiChatRequest request = new OpenAiChatRequest(
                "",
                List.of(system, user1, assistant, user2),
                true,
                objectMapper.createArrayNode()
        );

        var normalized = normalizer.normalize(request);

        assertEquals("latest", normalized.latestPrompt());
        assertEquals(4, normalized.messages().size());
        assertEquals(system, normalized.messages().get(0));
        assertEquals(user1, normalized.messages().get(1));
        assertEquals(assistant, normalized.messages().get(2));
        assertEquals(user2, normalized.messages().get(3));
        assertEquals("lite", normalized.model());
        assertTrue(normalized.stream());
        assertEquals(request.tools(), normalized.tools());
    }

    @Test
    void normalizePreservesStructuredMessagesAndContentParts() {
        var assistant = objectMapper.createObjectNode();
        assistant.put("role", "assistant");
        assistant.put("content", "Tool calls:\n[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]");

        var tool = objectMapper.createObjectNode();
        tool.put("role", "tool");
        tool.put("name", "ping");
        tool.put("tool_call_id", "call_1");
        tool.put("content", "pong");

        var user = objectMapper.createObjectNode();
        user.put("role", "user");
        var content = objectMapper.createArrayNode();
        content.add(objectMapper.createObjectNode().put("type", "text").put("text", "hello"));
        content.add(objectMapper.createObjectNode().put("type", "text").put("text", "world"));
        user.set("content", content);

        OpenAiChatRequest request = new OpenAiChatRequest(
                "Qwen3.7-Max",
                List.of(assistant, tool, user),
                false,
                objectMapper.createArrayNode()
        );

        var normalized = normalizer.normalize(request);

        assertEquals("Qwen3.7-Max", normalized.model());
        assertEquals(assistant, normalized.messages().get(0));
        assertEquals(tool, normalized.messages().get(1));
        assertEquals(user, normalized.messages().get(2));
        assertEquals("hello\n\nworld", normalized.latestPrompt());
    }

    private JsonNode message(String role, JsonNode content) {
        return objectMapper.createObjectNode()
                .put("role", role)
                .set("content", content);
    }
}
