package com.myroutine.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties("myroutine.web")
@Validated
public record CorsProperties(
        List<String> corsAllowedOrigins
) {
    public CorsProperties {
        if (corsAllowedOrigins == null) {
            corsAllowedOrigins = List.of();
        }
    }
}
