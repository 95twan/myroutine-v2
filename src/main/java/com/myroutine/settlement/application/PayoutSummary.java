package com.myroutine.settlement.application;

public record PayoutSummary(
        int paid,
        int payoutFailed
) {
}
