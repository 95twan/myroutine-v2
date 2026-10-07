package com.myroutine.product.infrastructure;

import com.myroutine.product.domain.Stock;
import com.myroutine.product.domain.StockRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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
}
