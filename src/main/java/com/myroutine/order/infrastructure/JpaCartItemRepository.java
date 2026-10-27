package com.myroutine.order.infrastructure;

import com.myroutine.order.domain.CartItem;
import com.myroutine.order.domain.CartItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface JpaCartItemRepository extends JpaRepository<CartItem, UUID>, CartItemRepository {

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO orders.cart_item (id, cart_id, product_id, quantity, version, created_at, updated_at)
                    VALUES (:id, :cartId, :productId, :quantity, 0, :now, :now)
                    ON CONFLICT ON CONSTRAINT uk_cart_item_cart_product
                    DO UPDATE SET quantity = LEAST(orders.cart_item.quantity + EXCLUDED.quantity, 99),
                    version = orders.cart_item.version + 1, updated_at = EXCLUDED.updated_at
                    """
    )
    int addQuantity(UUID id, UUID cartId, UUID productId, int quantity, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    DELETE FROM orders.cart_item
                    WHERE cart_id = :cartId AND product_id = :productId AND quantity <= :quantity
                    """
    )
    int deleteIfQuantityAtMost(UUID cartId, UUID productId, int quantity);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE orders.cart_item
                    SET quantity = quantity - :quantity, version = version + 1, updated_at = :now
                    WHERE cart_id = :cartId AND product_id = :productId AND quantity > :quantity
                    """
    )
    int deductQuantity(UUID cartId, UUID productId, int quantity, Instant now);

    @Modifying
    @Query("delete from CartItem ci where ci.cartId = :cartId")
    void deleteAllByCartId(UUID cartId);
}
