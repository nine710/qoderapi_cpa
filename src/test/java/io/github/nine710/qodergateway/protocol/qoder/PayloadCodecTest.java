package io.github.nine710.qodergateway.protocol.qoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayloadCodecTest {

    private final PayloadCodec codec = new PayloadCodec();

    @Test
    void encodesAndDecodesRoundTrip() {
        String source = "hello qoder gateway";

        String encoded = codec.encodeText(source);

        assertEquals(source, codec.decodeToText(encoded));
    }

    @Test
    void rejectsUnknownCharacter() {
        assertThrows(IllegalArgumentException.class, () -> codec.decodeToText("中文"));
    }
}
