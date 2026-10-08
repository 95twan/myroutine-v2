package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
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
public class AdjustStockService {
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ShopApi shopApi;
    private final Clock clock;

    @Transactional
    public StockResult adjust(UUID memberId, UUID shopId, UUID productId, int delta, String reason) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);
        Product product = productRepository.findByIdAndShopId(productId, shopId).orElseThrow(
                () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        );
        Instant now = Instant.now(clock);
        int row = stockRepository.adjust(product.getId(), delta, now);
        if (row == 0) {
            throw new BusinessException(ProductErrorCode.OUT_OF_STOCK);
        }
        stockRepository.insertMovement(
                Ids.newId(),
                product.getId(),
                StockMovementType.ADJUST.name(),
                delta,
                StockRefType.ADJUSTMENT.name(),
                Ids.newId(),
                reason,
                now
        );
        Stock stock = stockRepository.findById(product.getId()).orElseThrow(
                () -> new IllegalStateException("상품에 연결된 재고가 없습니다.")
        );

        return StockResult.from(stock);
    }
}
