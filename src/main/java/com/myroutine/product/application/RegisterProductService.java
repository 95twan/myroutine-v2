package com.myroutine.product.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.product.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterProductService {
    private final ShopApi shopApi;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final Clock clock;
    private final ProductEventPublisher publisher;

    @Transactional
    public UUID register(UUID memberId, UUID shopId, RegisterProductCommand command) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);

        Product product = Product.register(
                shopId,
                command.name(),
                command.description(),
                command.category(),
                Money.of(command.price()),
                command.subscribable()
        );

        Product savedProduct = productRepository.save(product);

        UUID productId = savedProduct.getId();

        publisher.upserted(savedProduct);

        Instant now = Instant.now(clock);
        stockRepository.insertStock(productId, command.initialStock(), now);
        stockRepository.insertMovement(
                Ids.newId(),
                productId,
                StockMovementType.RECEIVE.name(),
                command.initialStock(),
                StockRefType.PRODUCT_REGISTER.name(),
                productId,
                null,
                now
        );

        return productId;
    }
}
