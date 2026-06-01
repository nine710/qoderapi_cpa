package io.github.nine710.qodergateway.support.model;

public record QoderSession(
        String machineId,
        String machineToken,
        String machineType,
        String userId,
        String userName,
        String userType,
        String securityOauthToken,
        String refreshToken,
        String cosyKey,
        String info
) {
}
