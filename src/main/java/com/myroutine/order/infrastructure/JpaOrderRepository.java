package com.myroutine.order.infrastructure;

import com.myroutine.order.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaOrderRepository extends JpaRepository<Order, UUID>, OrderRepository {
    @EntityGraph(attributePaths = "shopOrders")
    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    @Query("""
                select o from Order o
                where o.memberId = :memberId
                order by o.createdAt desc, o.id desc
            """)
    List<Order> findByMemberFirstPage(UUID memberId, Limit limit);

    @Query("""
                select o from Order o
                where o.memberId = :memberId
                and (o.createdAt < :cursorCreatedAt or (o.createdAt = :cursorCreatedAt and o.id < :cursorId))
                order by o.createdAt desc, o.id desc
            """)
    List<Order> findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
            set o.status = :to, o.version = o.version + 1, o.updatedAt = :now
            where o.id = :id and o.status = :from and o.expiresAt > :now
            """)
    int startPayment(UUID id, OrderStatus from, OrderStatus to, Instant now);


    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
            set o.status = :to, o.version = o.version + 1, o.updatedAt = :now
            where o.id = :id and o.status = :from
            """)
    int cancelBeforePayment(UUID id, OrderStatus from, OrderStatus to, Instant now);

    @Query("""
            select o.id
            from Order o
            where o.status = :status and o.expiresAt <= :now
            order by o.expiresAt
            """)
    List<UUID> findExpiredIds(OrderStatus status, Instant now, Limit limit);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
            set o.status = :to, o.version = o.version + 1, o.updatedAt = :now
            where o.id = :id and o.status = :from and o.expiresAt <= :now
            """)
    int expire(UUID id, OrderStatus from, OrderStatus to, Instant now);

    @Query("""
            select o.id
            from Order o
            where o.status = :status and o.updatedAt < :before
            order by o.updatedAt
            """)
    List<UUID> findStuckIds(OrderStatus status, Instant before, Limit limit);

    @Query("""
            select o
            from Order o
            join o.shopOrders so
            where so.id = :shopOrderId
            """)
    Optional<Order> findByShopOrderId(UUID shopOrderId);

    @Query("""
            select new com.myroutine.order.domain.ShopOrderListRow(so.id, so.orderId, so.status, o.shippingAddress, so.carrier, so.trackingNumber, so.shippedAt, so.deliveredAt, so.createdAt)
            from ShopOrder so
            join Order o on o.id = so.orderId
            where so.shopId = :shopId and (:status is null or so.status = :status)
            order by so.createdAt desc, so.id desc
            """)
    List<ShopOrderListRow> findShopOrderFirstPage(UUID shopId, ShopOrderStatus status, Limit limit);

    @Query("""
            select new com.myroutine.order.domain.ShopOrderListRow(so.id, so.orderId, so.status, o.shippingAddress, so.carrier, so.trackingNumber, so.shippedAt, so.deliveredAt, so.createdAt)
            from ShopOrder so
            join Order o on o.id = so.orderId
            where so.shopId = :shopId and (:status is null or so.status = :status) and (so.createdAt < :cursorCreatedAt or (so.createdAt = :cursorCreatedAt and so.id < :cursorId))
            order by so.createdAt desc, so.id desc
            """)
    List<ShopOrderListRow> findShopOrderNextPage(UUID shopId, ShopOrderStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Query("""
            select ol
            from OrderLine ol
            where ol.shopOrderId in :shopOrderIds
            """)
    List<OrderLine> findLinesByShopOrderIdIn(Collection<UUID> shopOrderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.memberId = :memberId")
    Optional<Order> findByIdAndMemberIdForUpdate(UUID id, UUID memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o join o.shopOrders so where so.id = :shopOrderId")
    Optional<Order> findByShopOrderIdForUpdate(UUID shopOrderId);
}
