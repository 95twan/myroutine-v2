package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public record RefreshToken(
        UUID sessionId,
        String secret
) {
    public static RefreshToken generate(UUID sessionId) {
        byte[] randomBytes = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(randomBytes);

        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        return new RefreshToken(sessionId, secret);
    }

    public String value() {
        return sessionId + "." + secret;
    }

    public static RefreshToken parse(String raw) {
        if (raw == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String[] parts = raw.split("\\.");
        if (parts.length != 2) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        return new RefreshToken(sessionId, parts[1]);
    }

    public String secretHash() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(secret.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    @Override
    public String toString() {
        return "RefreshToken[sessionId=" + sessionId + ", secret=***]";
    }
}
