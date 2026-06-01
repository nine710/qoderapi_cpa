package io.github.nine710.qodergateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ChatResponseWriter {

    private final ObjectMapper objectMapper;

    public ChatResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter createEmitter() {
        return new SseEmitter(0L);
    }

    public void sendChunk(SseEmitter emitter, String id, long created, String model, String role, String content, ArrayNode toolCalls) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(buildChunk(id, created, model, role, content, toolCalls))));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write stream chunk", exception);
        }
    }

    public void sendDone(SseEmitter emitter, String id, long created, String model, String finishReason) {
        try {
            ObjectNode chunk = buildChunk(id, created, model, "", "", null);
            ObjectNode choice = (ObjectNode) chunk.path("choices").get(0);
            choice.set("delta", objectMapper.createObjectNode());
            choice.put("finish_reason", finishReason);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to finish stream", exception);
        }
    }

    public ObjectNode buildCompletion(String id, long created, String model, String content, ArrayNode toolCalls) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", id);
        root.put("object", "chat.completion");
        root.put("created", created);
        root.put("model", model);
        ArrayNode choices = objectMapper.createArrayNode();
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("index", 0);
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        if (toolCalls != null && toolCalls.size() > 0) {
            if (content == null || content.isBlank()) {
                message.putNull("content");
            } else {
                message.put("content", content);
            }
            message.set("tool_calls", toolCalls);
            choice.put("finish_reason", "tool_calls");
        } else {
            message.put("content", content == null ? "" : content);
            choice.put("finish_reason", "stop");
        }
        choice.set("message", message);
        choices.add(choice);
        root.set("choices", choices);
        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("completion_tokens", 0);
        usage.put("total_tokens", 0);
        root.set("usage", usage);
        return root;
    }

    private ObjectNode buildChunk(String id, long created, String model, String role, String content, ArrayNode toolCalls) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", id);
        root.put("object", "chat.completion.chunk");
        root.put("created", created);
        root.put("model", model);
        ArrayNode choices = objectMapper.createArrayNode();
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("index", 0);
        choice.putNull("finish_reason");
        ObjectNode delta = objectMapper.createObjectNode();
        if (role != null && !role.isBlank()) {
            delta.put("role", role);
        }
        if (content != null && !content.isBlank()) {
            delta.put("content", content);
        }
        if (toolCalls != null && toolCalls.size() > 0) {
            delta.set("tool_calls", toolCalls);
        }
        choice.set("delta", delta);
        choices.add(choice);
        root.set("choices", choices);
        return root;
    }
}
