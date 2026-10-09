package com.myroutine.product.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findDetailById(UUID id);

    Optional<Product> findByIdAndShopId(UUID id, UUID shopId);

    List<ProductListRow> findPublicFirstPage(ProductStatus status, ProductCategory category, Limit limit);

    List<ProductListRow> findPublicNextPage(ProductStatus status, ProductCategory category, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    List<ProductListRow> findByShopFirstPage(UUID shopId, Limit limit);

    List<ProductListRow> findByShopNextPage(UUID shopId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    int insertPriceHistory(UUID id, UUID productId, long oldPrice, long newPrice, Instant now);

    List<ProductCheckoutRow> findCheckoutRowsByIds(Collection<UUID> ids);
}
