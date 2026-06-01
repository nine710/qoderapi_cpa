package io.github.nine710.qodergateway.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nine710.qodergateway.api.ChatResponseWriter;
import io.github.nine710.qodergateway.infra.qoder.SignedGatewayClient;
import io.github.nine710.qodergateway.protocol.openai.ChatRequestNormalizer;
import io.github.nine710.qodergateway.protocol.qoder.StreamEventTranslator;
import io.github.nine710.qodergateway.protocol.qoder.UpstreamPayloadAssembler;
import io.github.nine710.qodergateway.support.model.OpenAiChatRequest;
import io.github.nine710.qodergateway.support.model.StreamDelta;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Service
public class ChatGatewayService {

    private final ChatRequestNormalizer chatRequestNormalizer;
    private final UpstreamPayloadAssembler upstreamPayloadAssembler;
    private final SessionFacade sessionFacade;
    private final SignedGatewayClient signedGatewayClient;
    private final StreamEventTranslator streamEventTranslator;
    private final ChatResponseWriter chatResponseWriter;
    private final ObjectMapper objectMapper;

    public ChatGatewayService(
            ChatRequestNormalizer chatRequestNormalizer,
            UpstreamPayloadAssembler upstreamPayloadAssembler,
            SessionFacade sessionFacade,
            SignedGatewayClient signedGatewayClient,
            StreamEventTranslator streamEventTranslator,
            ChatResponseWriter chatResponseWriter,
            ObjectMapper objectMapper
    ) {
        this.chatRequestNormalizer = chatRequestNormalizer;
        this.upstreamPayloadAssembler = upstreamPayloadAssembler;
        this.sessionFacade = sessionFacade;
        this.signedGatewayClient = signedGatewayClient;
        this.streamEventTranslator = streamEventTranslator;
        this.chatResponseWriter = chatResponseWriter;
        this.objectMapper = objectMapper;
    }

    public ObjectNode complete(OpenAiChatRequest request) {
        var normalized = chatRequestNormalizer.normalize(request);
        var payload = upstreamPayloadAssembler.assemble(normalized, sessionFacade.getSession().userType());
        String id = nextCompletionId();
        long created = System.currentTimeMillis() / 1000;
        StringBuilder content = new StringBuilder();
        ToolBuffer toolBuffer = new ToolBuffer(objectMapper);
        boolean toolsEnabled = normalized.tools() != null && normalized.tools().isArray() && normalized.tools().size() > 0;

        signedGatewayClient.openStreamLines(sessionFacade.getSession(), payload, line -> {
            if (!line.startsWith("data:")) {
                return;
            }
            StreamDelta delta = streamEventTranslator.translate(line.substring(5).trim());
            if (!delta.content().isBlank()) {
                content.append(delta.content());
            }
            if (delta.toolCalls() != null && delta.toolCalls().size() > 0) {
                toolBuffer.append(delta.toolCalls());
            }
        });

        ArrayNode fallbackToolCalls = null;
        if (toolBuffer.isEmpty() && toolsEnabled) {
            fallbackToolCalls = parseToolCallsText(content.toString());
        }
        String responseContent;
        ArrayNode responseToolCalls;
        if (fallbackToolCalls != null) {
            responseContent = null;
            responseToolCalls = fallbackToolCalls;
        } else if (content.isEmpty() && !toolBuffer.isEmpty()) {
            responseContent = null;
            responseToolCalls = toolBuffer.snapshotOr(null);
        } else {
            responseContent = content.toString();
            responseToolCalls = toolBuffer.snapshotOr(null);
        }
        return chatResponseWriter.buildCompletion(id, created, normalized.model(), responseContent, responseToolCalls);
    }

    public SseEmitter stream(OpenAiChatRequest request) {
        var normalized = chatRequestNormalizer.normalize(request);
        var payload = upstreamPayloadAssembler.assemble(normalized, sessionFacade.getSession().userType());
        String id = nextCompletionId();
        long created = System.currentTimeMillis() / 1000;
        SseEmitter emitter = chatResponseWriter.createEmitter();
        boolean toolsEnabled = normalized.tools() != null && normalized.tools().isArray() && normalized.tools().size() > 0;

        new Thread(() -> {
            try {
                StreamBuffer streamBuffer = new StreamBuffer(objectMapper, chatResponseWriter, emitter, id, created, normalized.model(), toolsEnabled);
                signedGatewayClient.openStreamLines(sessionFacade.getSession(), payload, line -> {
                    if (!line.startsWith("data:")) {
                        return;
                    }
                    StreamDelta delta = streamEventTranslator.translate(line.substring(5).trim());
                    if (delta.role().isBlank() && delta.content().isBlank() && (delta.toolCalls() == null || delta.toolCalls().isEmpty())) {
                        return;
                    }
                    streamBuffer.accept(delta);
                });
                streamBuffer.flush();
                chatResponseWriter.sendDone(emitter, id, created, normalized.model(), streamBuffer.finishReason());
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        }, "qoder-stream-proxy").start();

        return emitter;
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
            if (!parsed.isArray()) {
                return null;
            }
            return (ArrayNode) parsed.deepCopy();
        } catch (Exception exception) {
            return null;
        }
    }

    private String nextCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private static final class ToolBuffer {
        private final ObjectMapper objectMapper;
        private final ArrayNode toolCalls;

        private ToolBuffer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.toolCalls = objectMapper.createArrayNode();
        }

        private void append(ArrayNode deltaCalls) {
            for (JsonNode deltaCall : deltaCalls) {
                int index = deltaCall.path("index").isInt() ? deltaCall.path("index").asInt() : toolCalls.size();
                while (toolCalls.size() <= index) {
                    ObjectNode placeholder = objectMapper.createObjectNode();
                    placeholder.put("id", "");
                    placeholder.put("type", "function");
                    ObjectNode function = objectMapper.createObjectNode();
                    function.put("name", "");
                    function.put("arguments", "");
                    placeholder.set("function", function);
                    toolCalls.add(placeholder);
                }
                ObjectNode existing = (ObjectNode) toolCalls.get(index);
                if (deltaCall.path("id").isTextual()) {
                    existing.put("id", deltaCall.path("id").asText());
                }
                if (deltaCall.path("type").isTextual()) {
                    existing.put("type", deltaCall.path("type").asText());
                }
                JsonNode deltaFunction = deltaCall.path("function");
                ObjectNode existingFunction = (ObjectNode) existing.path("function");
                if (deltaFunction.path("name").isTextual()) {
                    existingFunction.put("name", deltaFunction.path("name").asText());
                }
                if (deltaFunction.path("arguments").isTextual()) {
                    existingFunction.put("arguments", existingFunction.path("arguments").asText("") + deltaFunction.path("arguments").asText());
                }
            }
        }

        private boolean isEmpty() {
            return toolCalls.isEmpty();
        }

        private ArrayNode snapshotOr(ArrayNode fallback) {
            if (!toolCalls.isEmpty()) {
                return toolCalls.deepCopy();
            }
            return fallback == null ? null : fallback.deepCopy();
        }
    }

    private static final class StreamBuffer {
        private final ObjectMapper objectMapper;
        private final ChatResponseWriter chatResponseWriter;
        private final SseEmitter emitter;
        private final String id;
        private final long created;
        private final String model;
        private final boolean toolCallFallbackEnabled;
        private final ToolBuffer toolBuffer;
        private final StringBuilder pendingContent = new StringBuilder();
        private String pendingRole = "assistant";
        private boolean emittedChunk;
        private boolean streamingText;

        private StreamBuffer(
                ObjectMapper objectMapper,
                ChatResponseWriter chatResponseWriter,
                SseEmitter emitter,
                String id,
                long created,
                String model,
                boolean toolCallFallbackEnabled
        ) {
            this.objectMapper = objectMapper;
            this.chatResponseWriter = chatResponseWriter;
            this.emitter = emitter;
            this.id = id;
            this.created = created;
            this.model = model;
            this.toolCallFallbackEnabled = toolCallFallbackEnabled;
            this.toolBuffer = new ToolBuffer(objectMapper);
        }

        private void accept(StreamDelta delta) {
            if (delta.role() != null && !delta.role().isEmpty()) {
                pendingRole = delta.role();
            }
            if (delta.toolCalls() != null && delta.toolCalls().size() > 0) {
                discardBufferedToolCallText();
                toolBuffer.append(delta.toolCalls());
                emit(null, withToolCallIndices(delta.toolCalls()));
                return;
            }
            if (delta.content() == null || delta.content().isEmpty()) {
                return;
            }
            if (!toolCallFallbackEnabled || streamingText) {
                streamingText = true;
                emit(delta.content(), null);
                return;
            }
            pendingContent.append(delta.content());
            if (isPotentialToolCallText(pendingContent.toString())) {
                return;
            }
            streamingText = true;
            emitBufferedText();
        }

        private void flush() {
            if (pendingContent.length() == 0) {
                return;
            }
            String buffered = pendingContent.toString();
            pendingContent.setLength(0);
            ArrayNode parsedToolCalls = toolCallFallbackEnabled ? parseToolCallsText(buffered) : null;
            if (parsedToolCalls != null) {
                toolBuffer.append(parsedToolCalls);
                emit(null, withToolCallIndices(parsedToolCalls));
                return;
            }
            streamingText = true;
            emit(buffered, null);
        }

        private String finishReason() {
            return toolBuffer.isEmpty() ? "stop" : "tool_calls";
        }

        private void emitBufferedText() {
            if (pendingContent.length() == 0) {
                return;
            }
            String buffered = pendingContent.toString();
            pendingContent.setLength(0);
            emit(buffered, null);
        }

        private void discardBufferedToolCallText() {
            if (pendingContent.length() == 0) {
                return;
            }
            String buffered = pendingContent.toString();
            pendingContent.setLength(0);
            if (toolCallFallbackEnabled && isPotentialToolCallText(buffered)) {
                return;
            }
            streamingText = true;
            emit(buffered, null);
        }

        private void emit(String content, ArrayNode toolCalls) {
            String role = "";
            if (!emittedChunk) {
                role = pendingRole == null || pendingRole.isBlank() ? "assistant" : pendingRole;
            }
            chatResponseWriter.sendChunk(emitter, id, created, model, role, content, toolCalls);
            emittedChunk = true;
        }

        private boolean isPotentialToolCallText(String text) {
            String candidate = text == null ? "" : text.stripLeading();
            if (candidate.isEmpty()) {
                return true;
            }
            return "Tool calls:".startsWith(candidate) || candidate.startsWith("Tool calls:");
        }

        private ArrayNode withToolCallIndices(ArrayNode rawToolCalls) {
            ArrayNode indexed = objectMapper.createArrayNode();
            for (int i = 0; i < rawToolCalls.size(); i++) {
                ObjectNode call = (ObjectNode) rawToolCalls.get(i).deepCopy();
                if (!call.path("index").isInt()) {
                    call.put("index", i);
                }
                indexed.add(call);
            }
            return indexed;
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
                if (!parsed.isArray()) {
                    return null;
                }
                return (ArrayNode) parsed.deepCopy();
            } catch (Exception exception) {
                return null;
            }
        }
    }
}
