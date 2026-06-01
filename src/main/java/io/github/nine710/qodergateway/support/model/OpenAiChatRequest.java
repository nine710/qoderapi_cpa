package io.github.nine710.qodergateway.support.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OpenAiChatRequest(
        String model,
        @NotEmpty List<JsonNode> messages,
        Boolean stream,
        JsonNode tools
) {
}
