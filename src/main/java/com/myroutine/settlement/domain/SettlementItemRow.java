package com.myroutine.settlement.domain;

import java.time.Instant;
import java.util.UUID;

public interface SettlementItemRow {
    UUID getId();
    UUID getOrderLineId();
    UUID getOrderId();
    UUID getProductId();
    long getAmount();
    Instant getConfirmedAt();
    Instant getCreatedAt();
}
