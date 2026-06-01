package io.github.nine710.qodergateway.infra.qoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nine710.qodergateway.support.model.QoderSession;
import io.github.nine710.qodergateway.support.model.SessionBootstrap;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class SessionTokenFactory {

    private static final String SERVER_PUBLIC_KEY_PEM = "-----BEGIN PUBLIC KEY-----\n"
            + "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDA8iMH5c02LilrsERw9t6Pv5Nc\n"
            + "4k6Pz1EaDicBMpdpxKduSZu5OANqUq8er4GM95omAGIOPOh+Nx0spthYA2BqGz+l\n"
            + "6HRkPJ7S236FZz73In/KVuLnwI8JJ2CbuJap8kvheCCZpmAWpb/cPx/3Vr/J6I17\n"
            + "XcW+ML9FoCI6AOvOzwIDAQAB\n"
            + "-----END PUBLIC KEY-----";

    private final ObjectMapper objectMapper;

    public SessionTokenFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public QoderSession create(SessionBootstrap bootstrap) {
        try {
            byte[] tempKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16).getBytes(StandardCharsets.US_ASCII);
            String cosyKey = Base64.getEncoder().encodeToString(rsaEncrypt(tempKey));
            String info = Base64.getEncoder().encodeToString(aesEncrypt(buildAuthPayload(bootstrap), tempKey));
            return new QoderSession(
                    bootstrap.machineId(),
                    bootstrap.machineToken(),
                    bootstrap.machineType(),
                    bootstrap.userId(),
                    bootstrap.userName(),
                    bootstrap.userType(),
                    bootstrap.securityOauthToken(),
                    bootstrap.refreshToken(),
                    cosyKey,
                    info
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create qoder session", exception);
        }
    }

    public String buildPayloadBase64(QoderSession session) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("cosyVersion", "0.1.43");
            payload.put("ideVersion", "");
            payload.put("info", session.info());
            payload.put("requestId", UUID.randomUUID().toString());
            payload.put("version", "v1");
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(new TreeMap<>(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build bearer payload", exception);
        }
    }

    public String signRequest(String payloadBase64, QoderSession session, String cosyDate, String encodedBody, String pathWithoutAlgo) {
        return SignatureSupport.md5Hex(payloadBase64 + "\n"
                + session.cosyKey() + "\n"
                + cosyDate + "\n"
                + encodedBody + "\n"
                + pathWithoutAlgo);
    }

    public String composeBearer(String payloadBase64, String signature) {
        return "Bearer COSY." + payloadBase64 + '.' + signature;
    }

    private byte[] buildAuthPayload(SessionBootstrap bootstrap) throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("name", bootstrap.userName());
        payload.put("aid", bootstrap.userId());
        payload.put("uid", bootstrap.userId());
        payload.put("yx_uid", "");
        payload.put("organization_id", "");
        payload.put("organization_name", "");
        payload.put("user_type", bootstrap.userType());
        payload.put("security_oauth_token", bootstrap.securityOauthToken());
        payload.put("refresh_token", bootstrap.refreshToken());
        return objectMapper.writeValueAsBytes(payload);
    }

    private byte[] rsaEncrypt(byte[] tempKey) throws Exception {
        String content = SERVER_PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] publicKey = Base64.getDecoder().decode(content);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(tempKey);
    }

    private byte[] aesEncrypt(byte[] plain, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
        return cipher.doFinal(plain);
    }
}
