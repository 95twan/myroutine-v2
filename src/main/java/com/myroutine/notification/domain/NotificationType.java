package com.myroutine.notification.domain;

import java.time.Duration;

public enum NotificationType {
    WELCOME(Duration.ofHours(24)),
    ORDER_PAID(Duration.ofHours(24)),
    SHOP_ORDER_RECEIVED(Duration.ofHours(24)),
    ORDER_CLOSED(Duration.ofHours(24)),
    SHIPPED(Duration.ofHours(24)),
    DELIVERED(Duration.ofHours(24)),
    REFUNDED(null),
    SETTLEMENT_PAID(null),
    BANNED(null);

    private final Duration validity;

    NotificationType(Duration validity) {
        this.validity = validity;
    }

    public Duration getValidity() {
        return validity;
    }
}
