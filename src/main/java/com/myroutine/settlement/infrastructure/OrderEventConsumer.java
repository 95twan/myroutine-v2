package com.myroutine.settlement.infrastructure;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.order.api.event.OrderLineConfirmedEvent;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.settlement.application.SettlementItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    private final InboxGuard inboxGuard;
    private final EventEnvelopes envelopes;
    private final SettlementItemService settlementItemService;

    @KafkaListener(topics = OrderTopics.ORDER_LINE_CONFIRMED, groupId = "settlement")
    public void onOrderLineConfirmed(String message) {
        TypedEnvelope<OrderLineConfirmedEvent> typed = envelopes.parse(message, OrderLineConfirmedEvent.class);
        inboxGuard.runOnce("settlement.order-line-confirmed", typed.envelope(), () -> {
            settlementItemService.register(typed.payload());
        });
    }
}
