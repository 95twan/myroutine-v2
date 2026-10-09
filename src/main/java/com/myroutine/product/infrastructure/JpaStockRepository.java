package com.myroutine.product.infrastructure;

import com.myroutine.product.domain.Stock;
import com.myroutine.product.domain.StockRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaStockRepository extends JpaRepository<Stock, UUID>, StockRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO product.stock (product_id, available, reserved, sold, received, created_at, updated_at)
                    VALUES (:productId, :quantity, 0, 0, :quantity, :now, :now)
                    """
    )
    int insertStock(UUID productId, int quantity, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO product.stock_movement (id, product_id, type, quantity, ref_type, ref_id, reason, created_at)
                    VALUES (:id, :productId, :type, :quantity, :refType, :refId, :reason, :now)
                    ON CONFLICT ON CONSTRAINT uk_stock_movement_type_ref DO NOTHING
                    """
    )
    int insertMovement(UUID id, UUID productId, String type, int quantity, String refType, UUID refId, String reason, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE product.stock
                    SET available = available + :delta, received = received + :delta, updated_at = :now
                    WHERE product_id = :productId AND available + :delta >= 0
                    """
    )
    int adjust(UUID productId, int delta, Instant now);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT product_id
                    FROM product.stock
                    WHERE available + reserved + sold <> received
                    """
    )
    List<UUID> findProductIdsWithBrokenBalance();

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE product.stock
                    SET available = available - :quantity, reserved = reserved + :quantity, updated_at = :now
                    WHERE product_id = :productId AND available >= :quantity
                    """
    )
    int reserve(UUID productId, int quantity, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE product.stock
                    SET reserved = reserved - :quantity, sold = sold + :quantity, updated_at = :now
                    WHERE product_id = :productId AND reserved >= :quantity
                    """
    )
    int commit(UUID productId, int quantity, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE product.stock
                    SET reserved = reserved - :quantity, available = available + :quantity, updated_at = :now
                    WHERE product_id = :productId AND reserved >= :quantity
                    """
    )
    int release(UUID productId, int quantity, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE product.stock
                    SET sold = sold - :quantity, available = available + :quantity, updated_at = :now
                    WHERE product_id = :productId AND sold >= :quantity
                    """
    )
    int restore(UUID productId, int quantity, Instant now);
}
