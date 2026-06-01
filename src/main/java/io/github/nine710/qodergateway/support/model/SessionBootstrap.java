package io.github.nine710.qodergateway.support.model;

public record SessionBootstrap(
        String userId,
        String userName,
        String userType,
        String securityOauthToken,
        String refreshToken,
        String machineId,
        String machineToken,
        String machineType
) {
}
