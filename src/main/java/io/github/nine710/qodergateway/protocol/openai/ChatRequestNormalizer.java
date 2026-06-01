package io.github.nine710.qodergateway.protocol.openai;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.nine710.qodergateway.support.model.NormalizedChatRequest;
import io.github.nine710.qodergateway.support.model.OpenAiChatRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatRequestNormalizer {

    public NormalizedChatRequest normalize(OpenAiChatRequest request) {
        List<JsonNode> normalizedMessages = new ArrayList<>();
        String latestPrompt = "";

        for (JsonNode message : request.messages()) {
            normalizedMessages.add(message.deepCopy());
            if ("user".equals(message.path("role").asText())) {
                String content = extractContent(message.path("content"));
                if (!content.isBlank()) {
                    latestPrompt = content;
                }
            }
        }

        String model = request.model();
        if (model == null || model.isBlank()) {
            model = "lite";
        }

        return new NormalizedChatRequest(
                model,
                normalizedMessages,
                Boolean.TRUE.equals(request.stream()),
                request.tools(),
                latestPrompt
        );
    }

    private String extractContent(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : content) {
                String text = extractContentPart(item);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join("\n\n", parts);
        }
        return extractContentPart(content);
    }

    private String extractContentPart(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return "";
        }
        if (item.isTextual()) {
            return item.asText();
        }
        if (item.isObject()) {
            if (item.path("text").isTextual()) {
                return item.path("text").asText();
            }
            if (item.path("content").isContainerNode()) {
                return extractContent(item.path("content"));
            }
        }
        return item.toString();
    }
}
