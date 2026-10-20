package com.myroutine.payment.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("myroutine.toss")
@Validated
public record TossProperties(
        @NotBlank
        String baseUrl,
        @NotBlank
        String secretKey,
        String clientKey,
        @NotNull
        Duration connectTimeout,
        @NotNull
        Duration readTimeout
) {
}
