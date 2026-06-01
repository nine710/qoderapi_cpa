package io.github.nine710.qodergateway.protocol.qoder;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class PayloadCodec {

    private static final String CUSTOM_ALPHABET = "_doRTgHZBKcGVjlvpC,@aFSx#DPuNJme&i*MzLOEn)sUrthbf%Y^w.(kIQyXqWA!";
    private static final String STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final char CUSTOM_PADDING = '$';

    private final int[] customToStandard = new int[128];
    private final int[] standardToCustom = new int[128];

    public PayloadCodec() {
        for (int i = 0; i < 128; i++) {
            customToStandard[i] = -1;
            standardToCustom[i] = -1;
        }
        for (int i = 0; i < 64; i++) {
            customToStandard[CUSTOM_ALPHABET.charAt(i)] = STANDARD_ALPHABET.charAt(i);
            standardToCustom[STANDARD_ALPHABET.charAt(i)] = CUSTOM_ALPHABET.charAt(i);
        }
        customToStandard[CUSTOM_PADDING] = '=';
        standardToCustom['='] = CUSTOM_PADDING;
    }

    public String encodeText(String plainText) {
        return encodeBytes(plainText.getBytes(StandardCharsets.UTF_8));
    }

    public String encodeBytes(byte[] plainBytes) {
        String standard = Base64.getEncoder().encodeToString(plainBytes);
        int length = standard.length();
        int segment = length / 3;
        String rotated = standard.substring(length - segment)
                + standard.substring(segment, length - segment)
                + standard.substring(0, segment);

        StringBuilder encoded = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int mapped = standardToCustom[rotated.charAt(i)];
            if (mapped < 0) {
                throw new IllegalArgumentException("Unsupported character in payload");
            }
            encoded.append((char) mapped);
        }
        return encoded.toString();
    }

    public String decodeToText(String encoded) {
        return new String(decodeToBytes(encoded), StandardCharsets.UTF_8);
    }

    public byte[] decodeToBytes(String encoded) {
        int length = encoded.length();
        StringBuilder mapped = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int value = encoded.charAt(i);
            int standard = value < 128 ? customToStandard[value] : -1;
            if (standard < 0) {
                throw new IllegalArgumentException("Unsupported encoded character");
            }
            mapped.append((char) standard);
        }

        int segment = length / 3;
        String restored = mapped.substring(length - segment)
                + mapped.substring(segment, length - segment)
                + mapped.substring(0, segment);
        return Base64.getDecoder().decode(restored);
    }
}
