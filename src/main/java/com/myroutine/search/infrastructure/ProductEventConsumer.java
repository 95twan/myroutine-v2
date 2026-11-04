package com.myroutine.search.infrastructure;

import com.myroutine.common.outbox.EventContext;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.product.api.event.ProductTopics;
import com.myroutine.product.api.event.ProductUpsertedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ProductIndexer indexer;
    private final EventEnvelopes envelopes;

    @KafkaListener(topics = ProductTopics.PRODUCT_UPSERTED, groupId = "search")
    public void onProductUpserted(String message) {
        TypedEnvelope<ProductUpsertedEvent> typed = envelopes.parse(message, ProductUpsertedEvent.class);
        EventContext.run(typed.envelope(), () -> {
            indexer.upsert(typed.payload(), "products");
        });
    }
}
