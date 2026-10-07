package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.product.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductQueryService {
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ShopApi shopApi;

    @Transactional(readOnly = true)
    public CursorPage<ProductSummaryResult> getPublicProducts(ProductCategory category, String cursor, int size) {
        List<ProductListRow> productListRows;
        if (cursor == null || cursor.isBlank()) {
            productListRows = productRepository.findPublicFirstPage(ProductStatus.ON_SALE, category, Limit.of(size + 1));
        } else {
            Cursor currentCursor = Cursor.decode(cursor);
            productListRows = productRepository.findPublicNextPage(
                    ProductStatus.ON_SALE,
                    category,
                    currentCursor.createdAt(),
                    currentCursor.id(),
                    Limit.of(size + 1)
            );
        }

        String nextCursor = null;

        if (productListRows.size() > size) {
            ProductListRow next = productListRows.get(size - 1);
            nextCursor = new Cursor(next.createdAt(), next.id()).encode();
            productListRows = productListRows.subList(0, size);
        }

        return new CursorPage<>(productListRows.stream().map(ProductSummaryResult::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public ProductDetailResult getProduct(UUID productId) {
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        );
        if (!product.isVisibleToPublic()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        Stock stock = stockRepository.findById(productId).orElseThrow(
                () -> new IllegalStateException("상품에 연결된 재고가 없습니다.")
        );

        return ProductDetailResult.from(product, stock);
    }

    @Transactional(readOnly = true)
    public CursorPage<SellerProductResult> getShopProducts(UUID memberId, UUID shopId, String cursor, int size) {
        shopApi.verifyOwner(shopId, memberId);
        List<ProductListRow> productListRows;
        if (cursor == null || cursor.isBlank()) {
            productListRows = productRepository.findByShopFirstPage(shopId, Limit.of(size + 1));
        } else {
            Cursor currentCursor = Cursor.decode(cursor);
            productListRows = productRepository.findByShopNextPage(
                    shopId,
                    currentCursor.createdAt(),
                    currentCursor.id(),
                    Limit.of(size + 1)
            );
        }

        String nextCursor = null;

        if (productListRows.size() > size) {
            ProductListRow next = productListRows.get(size - 1);
            nextCursor = new Cursor(next.createdAt(), next.id()).encode();
            productListRows = productListRows.subList(0, size);
        }

        return new CursorPage<>(productListRows.stream().map(SellerProductResult::from).toList(), nextCursor);
    }
}
