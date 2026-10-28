package com.myroutine.settlement.domain;

import java.time.Instant;

public interface UpcomingSettlementSum {
    int getItemCount();
    long getGrossAmount();
    Instant getFirstConfirmedAt();
}
