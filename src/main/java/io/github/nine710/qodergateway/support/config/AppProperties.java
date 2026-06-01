package io.github.nine710.qodergateway.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String qoderPat,
        String host,
        int port,
        int upstreamTimeoutSeconds
) {
}
