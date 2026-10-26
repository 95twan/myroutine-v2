package com.myroutine.settlement.application;

import java.util.UUID;

public interface PayoutGateway {
    String payout(UUID settlementId, UUID shopId, long amount);

}
