package com.myroutine.common.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("myroutine.storage")
@Validated
public record StorageProperties(
        @NotNull
        URI endpoint,
        @NotBlank
        String region,
        @NotBlank
        String accessKey,
        @NotBlank
        String secretKey,
        @NotBlank
        String bucket,
        @NotBlank
        String publicBaseUrl,
        @NotNull
        Duration uploadUrlTtl,
        @Positive
        long maxUploadBytes
) {
}
