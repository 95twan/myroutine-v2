package com.myroutine.product.web;

import com.myroutine.product.application.UploadUrlResult;

import java.time.Instant;
import java.util.Map;

public record UploadUrlResponse(
        String uploadUrl,
        Map<String,String> headers,
        String objectKey,
        Instant expiresAt
) {
    public static UploadUrlResponse from(UploadUrlResult result) {
        return new UploadUrlResponse(
                result.uploadUrl(),
                result.headers(),
                result.objectKey(),
                result.expiresAt()
        );
    }
}
