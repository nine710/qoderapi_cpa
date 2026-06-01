package io.github.nine710.qodergateway.protocol.qoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nine710.qodergateway.support.model.NormalizedChatRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class UpstreamPayloadAssembler {

    private final ObjectMapper objectMapper;
    private final JsonNode template;

    public UpstreamPayloadAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.template = loadTemplate();
    }

    public ObjectNode assemble(NormalizedChatRequest request, String userType) {
        ObjectNode payload = template.deepCopy();
        String requestId = UUID.randomUUID().toString();
        payload.put("request_id", requestId);
        payload.put("chat_record_id", requestId);
        payload.put("request_set_id", UUID.randomUUID().toString());
        payload.put("session_id", UUID.randomUUID().toString());
        payload.put("stream", true);
        payload.put("aliyun_user_type", userType);

        ObjectNode modelConfig = (ObjectNode) payload.path("model_config");
        modelConfig.put("key", request.model());

        ObjectNode business = (ObjectNode) payload.path("business");
        business.put("id", UUID.randomUUID().toString());
        business.put("begin_at", System.currentTimeMillis());
        business.put("name", request.latestPrompt().length() > 30 ? request.latestPrompt().substring(0, 30) : request.latestPrompt());

        ObjectNode context = (ObjectNode) payload.path("chat_context");
        ((ObjectNode) context.path("text")).put("text", request.latestPrompt());
        ((ObjectNode) context.path("extra").path("originalContent")).put("text", request.latestPrompt());

        boolean toolsEnabled = request.tools() != null && request.tools().isArray() && request.tools().size() > 0;
        payload.set("messages", buildQoderMessages((ArrayNode) payload.path("messages"), request.messages(), request.latestPrompt(), toolsEnabled));
        if (toolsEnabled) {
            payload.set("tools", request.tools().deepCopy());
        }
        return payload;
    }

    private ArrayNode buildQoderMessages(ArrayNode templateMessages, List<JsonNode> incomingMessages, String prompt, boolean toolsEnabled) {
        ArrayNode rebuilt = objectMapper.createArrayNode();
        boolean keepTemplateSystem = !hasRole(incomingMessages, "system");
        if (keepTemplateSystem) {
            for (JsonNode message : templateMessages) {
                if ("system".equals(message.path("role").asText())) {
                    rebuilt.add(message.deepCopy());
                }
            }
        }
        if (incomingMessages != null) {
            for (int i = 0; i < incomingMessages.size(); i++) {
                JsonNode message = incomingMessages.get(i);
                boolean allowStructuredToolCalls = toolsEnabled && hasResolvedToolResponse(incomingMessages, i);
                ObjectNode converted = convertIncomingMessage(message, toolsEnabled, allowStructuredToolCalls);
                if (converted != null) {
                    rebuilt.add(converted);
                }
            }
        }
        if (rebuilt.isEmpty() && !prompt.isBlank()) {
            rebuilt.add(buildUserMessage(prompt));
        }
        return rebuilt;
    }

    private boolean hasRole(List<JsonNode> messages, String role) {
        if (messages == null) {
            return false;
        }
        for (JsonNode message : messages) {
            if (role.equals(message.path("role").asText())) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode convertIncomingMessage(JsonNode message, boolean toolsEnabled, boolean allowStructuredToolCalls) {
        String role = message.path("role").asText("user");
        String text = normalizeMessageText(message);
        ArrayNode anyToolCalls = extractAnyToolCalls(message, text, toolsEnabled);
        ArrayNode structuredToolCalls = resolveStructuredToolCalls(message, text, toolsEnabled, allowStructuredToolCalls);

        if ("assistant".equals(role) && structuredToolCalls != null) {
            return buildAssistantToolCallMessage(text, structuredToolCalls);
        }

        if ("assistant".equals(role) && anyToolCalls != null && !allowStructuredToolCalls) {
            return buildStructuredMessage("assistant", summarizeUnresolvedToolCalls(anyToolCalls));
        }

        if (!toolsEnabled && message.path("tool_calls").isArray() && message.path("tool_calls").size() > 0) {
            text = joinSections(text, renderToolCalls(message.path("tool_calls")));
        }

        if ("tool".equals(role)) {
            if (toolsEnabled) {
                return buildToolMessage(message, text);
            }
            role = "user";
            text = renderToolResult(message, text);
        }

        if (text.isBlank()) {
            return null;
        }

        if ("user".equals(role)) {
            return buildUserMessage(text);
        }

        return buildStructuredMessage(role, text);
    }

    private boolean hasResolvedToolResponse(List<JsonNode> messages, int assistantIndex) {
        JsonNode message = messages.get(assistantIndex);
        if (!"assistant".equals(message.path("role").asText())) {
            return false;
        }
        boolean hasToolCalls = (message.path("tool_calls").isArray() && message.path("tool_calls").size() > 0)
                || parseToolCallsText(normalizeMessageText(message)) != null;
        if (!hasToolCalls) {
            return false;
        }
        for (int i = assistantIndex + 1; i < messages.size(); i++) {
            String nextRole = messages.get(i).path("role").asText();
            if ("tool".equals(nextRole)) {
                return true;
            }
            if ("assistant".equals(nextRole) || "user".equals(nextRole) || "system".equals(nextRole)) {
                return false;
            }
        }
        return false;
    }

    private ObjectNode buildUserMessage(String text) {
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", "");
        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "text");
        item.put("text", text);
        contents.add(item);
        userMessage.set("contents", contents);
        userMessage.set("response_meta", blankResponseMeta());
        userMessage.put("reasoning_content_signature", "");
        return userMessage;
    }

    private ObjectNode buildStructuredMessage(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", text == null ? "" : text);
        message.set("response_meta", blankResponseMeta());
        message.put("reasoning_content_signature", "");
        return message;
    }

    private ObjectNode buildAssistantToolCallMessage(String text, ArrayNode toolCalls) {
        String content = text == null ? "" : text;
        if (parseToolCallsText(content) != null) {
            content = "";
        }
        ObjectNode message = buildStructuredMessage("assistant", content);
        message.set("tool_calls", toolCalls.deepCopy());
        return message;
    }

    private ObjectNode buildToolMessage(JsonNode message, String text) {
        ObjectNode out = buildStructuredMessage("tool", text);
        if (message.path("name").isTextual()) {
            out.put("name", message.path("name").asText());
        }
        if (message.path("tool_call_id").isTextual()) {
            out.put("tool_call_id", message.path("tool_call_id").asText());
        }
        return out;
    }

    private String normalizeMessageText(JsonNode message) {
        String text = normalizeContent(message.path("content"));
        if (text.isBlank()) {
            text = normalizeContent(message.path("contents"));
        }
        return text;
    }

    private ArrayNode resolveStructuredToolCalls(JsonNode message, String text, boolean toolsEnabled, boolean allowStructuredToolCalls) {
        if (!toolsEnabled || !allowStructuredToolCalls) {
            return null;
        }
        return extractAnyToolCalls(message, text, true);
    }

    private ArrayNode extractAnyToolCalls(JsonNode message, String text, boolean toolsEnabled) {
        if (!toolsEnabled) {
            return null;
        }
        if (message.path("tool_calls").isArray() && message.path("tool_calls").size() > 0) {
            return normalizeToolCalls(message.path("tool_calls"));
        }
        return parseToolCallsText(text);
    }

    private String normalizeContent(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : content) {
                String part = normalizeContentPart(item);
                if (!part.isBlank()) {
                    parts.add(part);
                }
            }
            return String.join("\n\n", parts);
        }
        return normalizeContentPart(content);
    }

    private String normalizeContentPart(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return "";
        }
        if (item.isTextual()) {
            return item.asText();
        }
        if (item.isObject()) {
            String type = item.path("type").asText("");
            if (item.path("text").isTextual()) {
                return item.path("text").asText();
            }
            if (("image_url".equals(type) || "input_image".equals(type)) && item.path("image_url").path("url").isTextual()) {
                return "[image] " + item.path("image_url").path("url").asText();
            }
            if (item.path("content").isContainerNode()) {
                return normalizeContent(item.path("content"));
            }
        }
        return item.toString();
    }

    private String renderToolCalls(JsonNode toolCalls) {
        return "Tool calls:\n" + toolCalls;
    }

    private ArrayNode parseToolCallsText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("Tool calls:")) {
            return null;
        }
        String payload = trimmed.substring("Tool calls:".length()).trim();
        if (payload.startsWith("```") && payload.endsWith("```")) {
            int newline = payload.indexOf('\n');
            if (newline >= 0) {
                payload = payload.substring(newline + 1, payload.length() - 3).trim();
            }
        }
        if (!payload.startsWith("[")) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(payload);
            return normalizeToolCalls(parsed);
        } catch (Exception exception) {
            return null;
        }
    }

    private ArrayNode normalizeToolCalls(JsonNode rawToolCalls) {
        if (rawToolCalls == null || !rawToolCalls.isArray()) {
            return null;
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        for (JsonNode rawToolCall : rawToolCalls) {
            JsonNode function = rawToolCall.path("function");
            String name = function.path("name").asText("");
            String arguments = normalizeToolArguments(function.path("arguments"));
            if (name.isBlank() && arguments.isBlank()) {
                continue;
            }
            ObjectNode call = objectMapper.createObjectNode();
            call.put("id", rawToolCall.path("id").asText(""));
            call.put("type", rawToolCall.path("type").asText("function"));
            ObjectNode normalizedFunction = objectMapper.createObjectNode();
            normalizedFunction.put("name", name);
            normalizedFunction.put("arguments", arguments);
            call.set("function", normalizedFunction);
            normalized.add(call);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String summarizeUnresolvedToolCalls(ArrayNode toolCalls) {
        StringBuilder sb = new StringBuilder("Previously planned but unexecuted tool calls");
        int limit = Math.min(toolCalls.size(), 6);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            String name = toolCalls.get(i).path("function").path("name").asText("");
            names.add(name.isBlank() ? "unknown" : name);
        }
        if (!names.isEmpty()) {
            sb.append(": ").append(String.join(", ", names));
        }
        if (toolCalls.size() > limit) {
            sb.append(" and ").append(toolCalls.size() - limit).append(" more");
        }
        sb.append('.');
        return sb.toString();
    }

    private String normalizeToolArguments(JsonNode arguments) {
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            return "";
        }
        if (arguments.isTextual()) {
            return arguments.asText();
        }
        return arguments.toString();
    }

    private String renderToolResult(JsonNode message, String text) {
        String name = message.path("name").asText("");
        String toolCallId = message.path("tool_call_id").asText("");
        StringBuilder sb = new StringBuilder("Tool result");
        if (!name.isBlank()) {
            sb.append(" (").append(name).append(')');
        }
        if (!toolCallId.isBlank()) {
            sb.append(" [").append(toolCallId).append(']');
        }
        if (!text.isBlank()) {
            sb.append(":\n").append(text);
        }
        return sb.toString();
    }

    private String joinSections(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n\n" + second;
    }

    private JsonNode loadTemplate() {
        try (InputStream inputStream = new ClassPathResource("baseprompt.json").getInputStream()) {
            String raw = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            raw = raw.replace("{UUID1}", UUID.randomUUID().toString());
            raw = raw.replace("{UUID2}", UUID.randomUUID().toString());
            raw = raw.replace("{UUID3}", UUID.randomUUID().toString());
            raw = raw.replace("{UUID4}", UUID.randomUUID().toString());
            raw = raw.replace("{UUID5}", UUID.randomUUID().toString());
            raw = raw.replace("{TIME1}", String.valueOf(System.currentTimeMillis()));
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load baseprompt template", exception);
        }
    }

    private ObjectNode blankResponseMeta() {
        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("completion_tokens", 0);
        usage.put("total_tokens", 0);
        ObjectNode completion = objectMapper.createObjectNode();
        completion.put("reasoning_tokens", 0);
        usage.set("completion_tokens_details", completion);
        ObjectNode prompt = objectMapper.createObjectNode();
        prompt.put("cached_tokens", 0);
        usage.set("prompt_tokens_details", prompt);
        ObjectNode responseMeta = objectMapper.createObjectNode();
        responseMeta.put("id", "");
        responseMeta.set("usage", usage);
        return responseMeta;
    }
}
