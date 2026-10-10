package com.myroutine.product.infrastructure;

import com.myroutine.product.domain.StockReservation;
import com.myroutine.product.domain.StockReservationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface JpaStockReservationRepository extends JpaRepository<StockReservation, UUID>, StockReservationRepository {

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO product.stock_reservation (id, order_id, product_id, quantity, status, expires_at, created_at, updated_at)
                    VALUES (:id, :orderId, :productId, :quantity, 'HELD', :expiresAt, :now, :now)
                    """
    )
    int insertReservation(UUID id, UUID orderId, UUID productId, int quantity, Instant expiresAt, Instant now);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE product.stock_reservation
                    SET status = :to, updated_at = :now
                    WHERE id = :id AND status = :from
                    """
    )
    int transit(UUID id, String from, String to, Instant now);
}
