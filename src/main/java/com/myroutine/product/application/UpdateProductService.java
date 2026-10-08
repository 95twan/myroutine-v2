package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.product.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateProductService {
    private final ShopApi shopApi;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final Clock clock;

    @Transactional
    public ProductDetailResult update(UUID memberId, UUID shopId, UUID productId, UpdateProductCommand command) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);
        Product product = productRepository.findByIdAndShopId(productId, shopId).orElseThrow(
                () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        );
        Stock stock = stockRepository.findById(product.getId()).orElseThrow(
                () -> new IllegalStateException("상품에 연결된 재고가 없습니다.")
        );
        Money newPrice = command.price() == null ? null : Money.of(command.price());
        Optional<PriceChange> priceChange = product.update(
                command.name(),
                command.description(),
                command.category(),
                newPrice,
                command.subscribable()
        );

        priceChange.ifPresent(change -> productRepository.insertPriceHistory(
                Ids.newId(),
                product.getId(),
                change.oldPrice().amount(),
                change.newPrice().amount(),
                Instant.now(clock)
        ));

        return ProductDetailResult.from(product, stock);
    }

    @Transactional
    public ProductDetailResult changeStatus(UUID memberId, UUID shopId, UUID productId, ProductStatus to) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);
        Product product = productRepository.findByIdAndShopId(productId, shopId).orElseThrow(
                () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        );
        product.changeStatus(to);
        Stock stock = stockRepository.findById(product.getId()).orElseThrow(
                () -> new IllegalStateException("상품에 연결된 재고가 없습니다.")
        );
        return ProductDetailResult.from(product, stock);
    }
}
