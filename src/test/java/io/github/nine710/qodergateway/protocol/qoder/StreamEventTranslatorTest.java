package io.github.nine710.qodergateway.protocol.qoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamEventTranslatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StreamEventTranslator translator = new StreamEventTranslator(objectMapper);

    @Test
    void translatesQoderDeltaBody() {
        String event = "{\"body\":\"{\\\"choices\\\":[{\\\"delta\\\":{\\\"role\\\":\\\"assistant\\\",\\\"content\\\":\\\"hi\\\"}}]}\"}";

        var delta = translator.translate(event);

        assertEquals("assistant", delta.role());
        assertEquals("hi", delta.content());
    }

    @Test
    void translatesToolCallsFromDeltaBody() {
        String event = "{\"body\":\"{\\\"choices\\\":[{\\\"delta\\\":{\\\"tool_calls\\\":[{\\\"index\\\":0,\\\"id\\\":\\\"call_1\\\",\\\"type\\\":\\\"function\\\",\\\"function\\\":{\\\"name\\\":\\\"ping\\\",\\\"arguments\\\":\\\"{}\\\"}}]}}]}\"}";

        var delta = translator.translate(event);

        assertEquals("", delta.role());
        assertEquals("", delta.content());
        assertEquals("call_1", delta.toolCalls().get(0).path("id").asText());
        assertEquals("ping", delta.toolCalls().get(0).path("function").path("name").asText());
    }

    @Test
    void returnsEmptyForInvalidPayload() {
        var delta = translator.translate("not-json");

        assertEquals("", delta.role());
        assertEquals("", delta.content());
    }
}
