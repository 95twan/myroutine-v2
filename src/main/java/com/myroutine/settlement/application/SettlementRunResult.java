package com.myroutine.settlement.application;

public record SettlementRunResult(
        int settledShops,
        int paid,
        int payoutFailed
) {
}
