package io.github.nine710.qodergateway.infra.qoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class SignatureSupport {

    public static final String APPCODE = "cosy";
    public static final String SECRET = "d2FyLCB3YXIgbmV2ZXIgY2hhbmdlcw==";

    private SignatureSupport() {
    }

    public static String currentDate() {
        return DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                .format(ZonedDateTime.now(java.time.ZoneOffset.UTC));
    }

    public static String sign(String date) {
        return md5Hex(APPCODE + '&' + SECRET + '&' + date);
    }

    public static String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(32);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
