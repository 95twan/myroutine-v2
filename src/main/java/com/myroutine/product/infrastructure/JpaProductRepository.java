package com.myroutine.product.infrastructure;

import com.myroutine.product.domain.*;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProductRepository extends JpaRepository<Product, UUID>, ProductRepository {

    @EntityGraph(attributePaths = "images")
    Optional<Product> findDetailById(UUID id);

    @Query("""
                    select new com.myroutine.product.domain.ProductListRow(p.id, p.shopId, p.name, p.category, p.price, p.status, p.thumbnailKey, p.createdAt, s.available, s.reserved, s.sold, s.received)
                    from Product p
                    join Stock s on s.productId = p.id
                    where p.status = :status
                            and (:category is null or p.category = :category)
                    order by p.createdAt desc, p.id desc
            """)
    List<ProductListRow> findPublicFirstPage(ProductStatus status, ProductCategory category, Limit limit);

    @Query("""
                   select new com.myroutine.product.domain.ProductListRow(p.id, p.shopId, p.name, p.category, p.price, p.status, p.thumbnailKey, p.createdAt, s.available, s.reserved, s.sold, s.received)
                   from Product p
                   join Stock s on s.productId = p.id
                   where p.status = :status
                           and (:category is null or p.category = :category)
                           and (p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
                   order by p.createdAt desc, p.id desc
            """)
    List<ProductListRow> findPublicNextPage(ProductStatus status, ProductCategory category, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Query("""
                   select new com.myroutine.product.domain.ProductListRow(p.id, p.shopId, p.name, p.category, p.price, p.status, p.thumbnailKey, p.createdAt, s.available, s.reserved, s.sold, s.received)
                   from Product p
                   join Stock s on s.productId = p.id
                   where p.shopId = :shopId
                   order by p.createdAt desc, p.id desc
            """)
    List<ProductListRow> findByShopFirstPage(UUID shopId, Limit limit);

    @Query("""
                   select new com.myroutine.product.domain.ProductListRow(p.id, p.shopId, p.name, p.category, p.price, p.status, p.thumbnailKey, p.createdAt, s.available, s.reserved, s.sold, s.received)
                   from Product p
                   join Stock s on s.productId = p.id
                   where p.shopId = :shopId
                            and (p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
                   order by p.createdAt desc, p.id desc
            """)
    List<ProductListRow> findByShopNextPage(UUID shopId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                           insert into product.price_history (id, product_id, old_price, new_price, changed_at, created_at)
                           values (:id, :productId, :oldPrice, :newPrice, :now, :now)
                    """
    )
    int insertPriceHistory(UUID id, UUID productId, long oldPrice, long newPrice, Instant now);

    @Query("""
                select new com.myroutine.product.domain.ProductCheckoutRow(p.id, p.shopId, p.name, p.thumbnailKey, p.price, p.status, p.subscribable, s.available)
                from Product p
                join Stock s on s.productId = p.id
                where p.id in :ids
            """)
    List<ProductCheckoutRow> findCheckoutRowsByIds(Collection<UUID> ids);
}
