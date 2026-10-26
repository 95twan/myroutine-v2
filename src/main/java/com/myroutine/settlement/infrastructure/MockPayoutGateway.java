package com.myroutine.settlement.infrastructure;

import com.myroutine.common.model.Ids;
import com.myroutine.settlement.application.PayoutGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MockPayoutGateway implements PayoutGateway {

    private final ConcurrentHashMap<UUID, String> payouts = new ConcurrentHashMap<>();

    @Override
    public String payout(UUID settlementId, UUID shopId, long amount) {
        String reference = payouts.computeIfAbsent(settlementId, id -> "PAYOUT-" + Ids.newId());
        log.info("payout settlementId={} shopId={} amount={} reference={}", settlementId, shopId, amount, reference);
        return reference;
    }
}
