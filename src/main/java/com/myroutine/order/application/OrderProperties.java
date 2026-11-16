package com.myroutine.order.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("myroutine.order")
@Validated
public record OrderProperties(
        @NotNull Duration paymentTtl,
        @Positive int maxPendingOrders,
        @Positive int unpaidLimit,
        @NotNull Duration unpaidWindow
) {
}
