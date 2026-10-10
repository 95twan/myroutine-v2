package com.myroutine.common.web;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

public record Cursor(
        Instant createdAt,
        UUID id
) {
    public String encode() {
        String cursor = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(cursor.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            String[] parts = new String(decoded, StandardCharsets.UTF_8).split("\\|");
            if (parts.length != 2) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            }
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
