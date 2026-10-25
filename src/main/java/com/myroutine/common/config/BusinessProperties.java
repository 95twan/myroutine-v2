package com.myroutine.common.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@ConfigurationProperties("myroutine")
@Validated
public record BusinessProperties(
        @NotNull ZoneId businessZone
) {
}
