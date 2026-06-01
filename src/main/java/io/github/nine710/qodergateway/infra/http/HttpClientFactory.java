package io.github.nine710.qodergateway.infra.http;

import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class HttpClientFactory {

    public HttpClient create(int timeoutSeconds) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
