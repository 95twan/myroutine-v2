package com.myroutine.payment.domain;

import java.time.Instant;

public sealed interface PgResult permits PgResult.Approved, PgResult.Rejected, PgResult.Unknown {
    record Approved(String paymentKey, long totalAmount, long balanceAmount, String method, Instant approvedAt) implements PgResult {}
    record Rejected(String code, String message) implements PgResult {}
    record Unknown(String reason) implements PgResult {}
}
