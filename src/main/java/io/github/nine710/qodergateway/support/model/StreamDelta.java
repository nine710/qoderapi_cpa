package io.github.nine710.qodergateway.support.model;

import com.fasterxml.jackson.databind.node.ArrayNode;

public record StreamDelta(
        String role,
        String content,
        ArrayNode toolCalls
) {

    public static StreamDelta empty() {
        return new StreamDelta("", "", null);
    }
}
