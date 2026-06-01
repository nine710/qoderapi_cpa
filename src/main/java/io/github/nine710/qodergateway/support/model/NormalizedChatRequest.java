package io.github.nine710.qodergateway.support.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record NormalizedChatRequest(
        String model,
        List<JsonNode> messages,
        boolean stream,
        JsonNode tools,
        String latestPrompt
) {
}
