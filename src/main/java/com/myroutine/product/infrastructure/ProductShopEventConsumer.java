package com.myroutine.product.infrastructure;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.product.application.ProductDiscontinueService;
import com.myroutine.shop.api.event.ShopClosedEvent;
import com.myroutine.shop.api.event.ShopTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductShopEventConsumer {
    private final ProductDiscontinueService productDiscontinueService;
    private final InboxGuard inboxGuard;
    private final EventEnvelopes envelopes;

    @KafkaListener(topics = ShopTopics.SHOP_CLOSED, groupId = "product")
    public void onShopClosed(String message) {
        TypedEnvelope<ShopClosedEvent> typed = envelopes.parse(message, ShopClosedEvent.class);
        inboxGuard.runOnce("product.shop-closed", typed.envelope(), () -> {
            productDiscontinueService.discontinueAllByShop(typed.payload().shopId());
        });
    }
}
