package com.myroutine.order.infrastructure;

import com.myroutine.order.domain.Cart;
import com.myroutine.order.domain.CartRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface JpaCartRepository extends JpaRepository<Cart, UUID>, CartRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO orders.cart (id, member_id, created_at, updated_at)
                    VALUES (:id, :memberId, :now, :now)
                    ON CONFLICT ON CONSTRAINT uk_cart_member DO NOTHING
                    """
    )
    int insertIfAbsent(UUID id, UUID memberId, Instant now);
}
