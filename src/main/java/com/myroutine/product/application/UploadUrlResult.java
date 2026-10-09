package com.myroutine.product.application;

import java.time.Instant;
import java.util.Map;

public record UploadUrlResult(
        String uploadUrl,
        Map<String,String> headers,
        String objectKey,
        Instant expiresAt
) {
}
