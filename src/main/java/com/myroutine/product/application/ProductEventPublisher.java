package com.myroutine.product.application;

import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.product.api.event.ProductDiscontinuedEvent;
import com.myroutine.product.api.event.ProductPriceChangedEvent;
import com.myroutine.product.api.event.ProductTopics;
import com.myroutine.product.api.event.ProductUpsertedEvent;
import com.myroutine.product.domain.PriceChange;
import com.myroutine.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ProductEventPublisher {
    private final OutboxPublisher publisher;

    public void upserted(Product product) {
        ProductUpsertedEvent event = new ProductUpsertedEvent(
                product.getId(),
                product.getShopId(),
                product.getName(),
                product.getDescription(),
                product.getCategory().name(),
                product.getPrice().amount(),
                product.getStatus().name(),
                product.isSubscribable(),
                product.getVersion()
        );
        publisher.publish(ProductTopics.PRODUCT_UPSERTED, "PRODUCT", product.getId(), event);
    }

    public void priceChanged(Product product, PriceChange change, Instant changedAt) {
        ProductPriceChangedEvent event = new ProductPriceChangedEvent(
                product.getId(),
                change.oldPrice().amount(),
                change.newPrice().amount(),
                changedAt
        );
        publisher.publish(ProductTopics.PRODUCT_PRICE_CHANGED, "PRODUCT", product.getId(), event);
    }

    public void discontinued(Product product, String reason) {
        ProductDiscontinuedEvent event = new ProductDiscontinuedEvent(product.getId(), product.getShopId(), reason);
        publisher.publish(ProductTopics.PRODUCT_DISCONTINUED, "PRODUCT", product.getId(), event);
    }
}
