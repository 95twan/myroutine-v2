package com.myroutine.order.application;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("myroutine.order")
@Validated
public record OrderProperties(
        @NotNull Duration paymentTtl
) {
}
