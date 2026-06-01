package io.github.nine710.qodergateway.infra.qoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nine710.qodergateway.infra.http.HttpClientFactory;
import io.github.nine710.qodergateway.protocol.qoder.PayloadCodec;
import io.github.nine710.qodergateway.support.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapHttpClientTest {

    @Test
    void wrapsNon200ResponseBodyInExceptionMessage() {
        AppProperties properties = new AppProperties("pat", "0.0.0.0", 8888, 1);
        BootstrapHttpClient client = new BootstrapHttpClient(
                new ObjectMapper(),
                new HttpClientFactory(),
                new PayloadCodec(),
                properties
        ) {
            @Override
            protected com.fasterxml.jackson.databind.JsonNode exchangeJobToken(String machineId, String machineToken, String machineType) {
                throw new IllegalStateException("jobToken HTTP 401 body={\"message\":\"bad token\"}");
            }
        };

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                client::exchangeSession
        );

        assertTrue(error.getMessage().contains("Failed to exchange qoder bootstrap session"));
        assertTrue(error.getCause().getMessage().contains("jobToken HTTP 401 body="));
    }
}
