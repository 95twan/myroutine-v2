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
        Instant now = Instant.now(clock);
        stockRepository.insertStock(savedProduct.getId(), command.initialStock(), now);
        stockRepository.insertMovement(
                Ids.newId(),
                savedProduct.getId(),
                StockMovementType.RECEIVE.name(),
                command.initialStock(),
                StockRefType.PRODUCT_REGISTER.name(),
                savedProduct.getId(),
                null,
                now
        );

        return savedProduct.getId();
    }
}
