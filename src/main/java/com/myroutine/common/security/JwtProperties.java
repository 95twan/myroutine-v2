package com.myroutine.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("myroutine.jwt")
@Validated
public record JwtProperties(
        @NotBlank
        String secret,
        @NotNull
        Duration accessTokenTtl
) {
    public JwtProperties {
        if (Base64.getDecoder().decode(secret).length < 32) {
            throw new IllegalArgumentException("Secret must be at least 32 bytes long");
        }
    }
}
