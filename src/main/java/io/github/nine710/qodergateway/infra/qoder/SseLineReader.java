package io.github.nine710.qodergateway.infra.qoder;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
public class SseLineReader {

    public void read(InputStream inputStream, Consumer<String> onLine) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    onLine.accept(line);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read upstream stream", exception);
        }
    }
}
