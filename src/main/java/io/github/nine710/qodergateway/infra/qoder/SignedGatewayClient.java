package io.github.nine710.qodergateway.infra.qoder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nine710.qodergateway.infra.http.HttpClientFactory;
import io.github.nine710.qodergateway.protocol.qoder.PayloadCodec;
import io.github.nine710.qodergateway.support.config.AppProperties;
import io.github.nine710.qodergateway.support.model.QoderSession;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

@Component
public class SignedGatewayClient {

    private static final String UPSTREAM_URL = "https://api3.qoder.sh/algo/api/v2/service/pro/sse/agent_chat_generation?FetchKeys=llm_model_result&AgentId=agent_common&Encode=1";

    private final HttpClient httpClient;
    private final PayloadCodec payloadCodec;
    private final SessionTokenFactory sessionTokenFactory;
    private final SseLineReader sseLineReader;

    public SignedGatewayClient(
            HttpClientFactory httpClientFactory,
            PayloadCodec payloadCodec,
            SessionTokenFactory sessionTokenFactory,
            SseLineReader sseLineReader,
            AppProperties appProperties
    ) {
        this.httpClient = httpClientFactory.create(appProperties.upstreamTimeoutSeconds());
        this.payloadCodec = payloadCodec;
        this.sessionTokenFactory = sessionTokenFactory;
        this.sseLineReader = sseLineReader;
    }

    public void openStreamLines(QoderSession session, ObjectNode payload, Consumer<String> onLine) {
        try {
            URI uri = URI.create(UPSTREAM_URL);
            String encodedBody = payloadCodec.encodeBytes(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String payloadBase64 = sessionTokenFactory.buildPayloadBase64(session);
            String cosyDate = String.valueOf(System.currentTimeMillis() / 1000);
            String path = uri.getRawPath().startsWith("/algo") ? uri.getRawPath().substring(5) : uri.getRawPath();
            String signature = sessionTokenFactory.signRequest(payloadBase64, session, cosyDate, encodedBody, path);
            String bearer = sessionTokenFactory.composeBearer(payloadBase64, signature);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(java.time.Duration.ofMinutes(5))
                    .header("cosy-data-policy", "AGREE")
                    .header("content-type", "application/json")
                    .header("cosy-machinetype", session.machineType())
                    .header("cosy-clienttype", "5")
                    .header("cosy-date", cosyDate)
                    .header("cosy-user", session.userId())
                    .header("cosy-key", session.cosyKey())
                    .header("cache-control", "no-cache")
                    .header("accept", "text/event-stream")
                    .header("cosy-clientip", "169.254.198.161")
                    .header("authorization", bearer)
                    .header("accept-encoding", "identity")
                    .header("cosy-version", "0.1.43")
                    .header("cosy-machineid", session.machineId())
                    .header("cosy-machinetoken", session.machineToken())
                    .header("login-version", "v2")
                    .header("user-agent", "Go-http-client/2.0")
                    .header("x-model-key", payload.path("model_config").path("key").asText("lite"))
                    .header("x-model-source", payload.path("model_config").path("source").asText("system"))
                    .POST(HttpRequest.BodyPublishers.ofString(encodedBody))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                byte[] errorBody = response.body().readAllBytes();
                throw new IllegalStateException("Upstream stream failed with status " + response.statusCode() + " body=" + new String(errorBody, java.nio.charset.StandardCharsets.UTF_8));
            }
            sseLineReader.read(response.body(), onLine);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to open qoder upstream stream", exception);
        }
    }
}
