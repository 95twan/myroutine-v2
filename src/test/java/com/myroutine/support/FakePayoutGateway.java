package com.myroutine.support;

import com.myroutine.common.model.Ids;
import com.myroutine.settlement.application.PayoutGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Primary
public class FakePayoutGateway implements PayoutGateway {

    private final List<PayoutCall> calls = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger failCount = new AtomicInteger();
    private final ConcurrentHashMap<UUID, String> payouts = new ConcurrentHashMap<>();

    @Override
    public String payout(UUID settlementId, UUID shopId, long amount) {
        calls.add(new PayoutCall(settlementId, shopId, amount));
        if (failCount.getAndDecrement() > 0) {
            throw new RuntimeException("Payout failed");
        }
        String reference = payouts.computeIfAbsent(settlementId, id -> "PAYOUT-" + Ids.newId());
        log.info("payout settlementId={} shopId={} amount={} reference={}", settlementId, shopId, amount, reference);
        return reference;
    }

    public List<PayoutCall> calls() {
        return List.copyOf(calls);
    }

    public void failNext(int times) {
        failCount.set(times);
    }

    public void reset() {
        calls.clear();
        payouts.clear();
        failCount.set(0);
    }
}
