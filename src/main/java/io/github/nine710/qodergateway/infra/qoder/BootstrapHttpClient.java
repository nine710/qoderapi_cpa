package io.github.nine710.qodergateway.infra.qoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nine710.qodergateway.infra.http.HttpClientFactory;
import io.github.nine710.qodergateway.protocol.qoder.PayloadCodec;
import io.github.nine710.qodergateway.support.config.AppProperties;
import io.github.nine710.qodergateway.support.model.SessionBootstrap;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Component
public class BootstrapHttpClient {

    private static final URI JOB_TOKEN_URI = URI.create("https://center.qoder.sh/algo/api/v3/user/jobToken?Encode=1");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final PayloadCodec payloadCodec;
    private final AppProperties appProperties;

    public BootstrapHttpClient(
            ObjectMapper objectMapper,
            HttpClientFactory httpClientFactory,
            PayloadCodec payloadCodec,
            AppProperties appProperties
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClientFactory.create(appProperties.upstreamTimeoutSeconds());
        this.payloadCodec = payloadCodec;
        this.appProperties = appProperties;
    }

    public SessionBootstrap exchangeSession() {
        try {
            String machineId = UUID.randomUUID().toString();
            String machineToken = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((UUID.randomUUID().toString() + UUID.randomUUID())
                            .substring(0, 50)
                            .getBytes(StandardCharsets.UTF_8));
            String machineType = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
            JsonNode json = exchangeJobToken(machineId, machineToken, machineType);
            sendHeartbeat(machineId, machineToken, machineType);
            return new SessionBootstrap(
                    json.path("id").asText(""),
                    json.path("name").asText(""),
                    json.path("userType").asText("personal_standard"),
                    json.path("securityOauthToken").asText(""),
                    json.path("refreshToken").asText(""),
                    machineId,
                    machineToken,
                    machineType
            );
        } catch (Exception exception) {
            String cause = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            throw new IllegalStateException("Failed to exchange qoder bootstrap session: " + cause, exception);
        }
    }

    public void sendHeartbeat(String machineId, String machineToken, String machineType) {
        try {
            ObjectNode hb = objectMapper.createObjectNode();
            hb.put("event_time", System.currentTimeMillis());
            hb.put("event_type", "cosy_heartbeat");
            hb.put("mid", machineId);
            String osArch = System.getProperty("os.arch");
            hb.put("os_arch", "amd64".equals(osArch) ? "windows_amd64" : osArch);
            hb.put("os_version", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            hb.put("ide_type", "qodercli");
            hb.put("ide_version", "0.1.43");
            hb.putObject("extra_info");

            String date = SignatureSupport.currentDate();
            String body = payloadCodec.encodeBytes(objectMapper.writeValueAsBytes(hb));
            URI heartbeatUri = URI.create("https://center.qoder.sh/algo/api/v1/heartbeat?Encode=1");
            HttpRequest request = HttpRequest.newBuilder(heartbeatUri)
                    .timeout(Duration.ofSeconds(appProperties.upstreamTimeoutSeconds()))
                    .header("cosy-machinetoken", machineToken)
                    .header("cosy-machinetype", machineType)
                    .header("login-version", "v2")
                    .header("appcode", SignatureSupport.APPCODE)
                    .header("accept", "application/json")
                    .header("accept-encoding", "identity")
                    .header("cosy-version", "0.1.43")
                    .header("cosy-clienttype", "5")
                    .header("date", date)
                    .header("signature", SignatureSupport.sign(date))
                    .header("content-type", "application/json")
                    .header("cosy-machineid", machineId)
                    .header("user-agent", "Go-http-client/2.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            System.err.println("[gateway] heartbeat failed: " + exception.getMessage());
        }
    }

    protected JsonNode exchangeJobToken(String machineId, String machineToken, String machineType) throws Exception {
        ObjectNode inner = objectMapper.createObjectNode();
        inner.put("personalToken", appProperties.qoderPat());
        inner.put("securityOauthToken", "");
        inner.put("refreshToken", "");
        inner.put("needRefresh", false);
        inner.putObject("authInfo");
        ObjectNode outer = objectMapper.createObjectNode();
        outer.put("payload", objectMapper.writeValueAsString(inner));
        outer.put("encodeVersion", "1");

        String date = SignatureSupport.currentDate();
        String body = payloadCodec.encodeBytes(objectMapper.writeValueAsBytes(outer));
        HttpRequest request = HttpRequest.newBuilder(JOB_TOKEN_URI)
                .timeout(Duration.ofSeconds(appProperties.upstreamTimeoutSeconds()))
                .header("cosy-machinetoken", machineToken)
                .header("cosy-machinetype", machineType)
                .header("login-version", "v2")
                .header("appcode", SignatureSupport.APPCODE)
                .header("accept", "application/json")
                .header("accept-encoding", "identity")
                .header("cosy-version", "0.1.43")
                .header("cosy-clienttype", "5")
                .header("date", date)
                .header("signature", SignatureSupport.sign(date))
                .header("content-type", "application/json")
                .header("cosy-machineid", machineId)
                .header("user-agent", "Go-http-client/2.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("jobToken HTTP " + response.statusCode() + " body=" + response.body());
        }
        return objectMapper.readTree(response.body());
    }
}
