package com.myroutine.order.infrastructure;

import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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
}
