package com.myroutine.order.application;

import com.myroutine.support.Behavior;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.StockSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExpirationServiceTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final OrderExpirationService orderExpirationService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OrderExpirationServiceTest(TestFixtures testFixtures, OrderExpirationService orderExpirationService, JdbcTemplate jdbcTemplate) {
        this.testFixtures = testFixtures;
        this.orderExpirationService = orderExpirationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("결제 대기 시간이 지난 주문을 만료시키고 재고와 예약을 되돌린다.")
    void expireBatchExpiresOrderAndReleasesStock() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);
        UUID orderId = order.orderId();
        testFixtures.setOrderExpired(orderId, Instant.now().minus(Duration.ofHours(1)));

        // When
        int batchCount = orderExpirationService.expireBatch();

        // Then
        assertThat(batchCount).isEqualTo(1);

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(100);
        assertThat(snapshot.reserved()).isEqualTo(0);

        Map<String, Object> stockReservation = jdbcTemplate.queryForMap(
                "SELECT status FROM product.stock_reservation WHERE order_id = ? AND product_id = ?", orderId, productId);
        assertThat(stockReservation.get("status")).isEqualTo("EXPIRED");

        Map<String, Object> o = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(o.get("status")).isEqualTo("EXPIRED");

        List<String> shopOrders = jdbcTemplate.queryForList(
                "SELECT status FROM orders.shop_order WHERE order_id = ?", String.class, orderId);
        assertThat(shopOrders).isNotEmpty().containsOnly("CANCELLED");

        List<String> lines = jdbcTemplate.queryForList("""
                SELECT ol.status FROM orders.order_line ol
                JOIN orders.shop_order so ON so.id = ol.shop_order_id
                WHERE so.order_id = ?
                """, String.class, orderId);
        assertThat(lines).isNotEmpty().containsOnly("CANCELLED");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 진행 중인 주문은 만료 시각이 지나도 만료시키지 않는다.")
    void expireBatchSkipsPaymentInProgressOrder() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);
        UUID orderId = order.orderId();
        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        testFixtures.confirmPayment(buyerId, orderId);
        testFixtures.setOrderExpired(orderId, Instant.now().minus(Duration.ofHours(1)));

        // When
        int batchCount = orderExpirationService.expireBatch();

        // Then
        assertThat(batchCount).isEqualTo(0);

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(99);
        assertThat(snapshot.reserved()).isEqualTo(1);

        Map<String, Object> o = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(o.get("status")).isEqualTo("PAYMENT_IN_PROGRESS");
    }

    @Test
    @DisplayName("아직 만료 시각이 지나지 않은 주문은 그대로 둔다.")
    void expireBatchKeepsNotYetExpiredOrder() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);
        UUID orderId = order.orderId();

        // When
        int batchCount = orderExpirationService.expireBatch();

        // Then
        assertThat(batchCount).isEqualTo(0);

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(99);
        assertThat(snapshot.reserved()).isEqualTo(1);

        Map<String, Object> o = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(o.get("status")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("한 주문의 처리가 실패해도 나머지 주문은 만료시킨다.")
    void expireBatchContinuesWhenOneOrderFails() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 2000, 100);
        UUID productId3 = testFixtures.registerProduct(memberId, shopId, 3000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order1 = testFixtures.createPendingOrder(buyerId, productId1);
        CheckoutResult order2 = testFixtures.createPendingOrder(buyerId, productId2);
        CheckoutResult order3 = testFixtures.createPendingOrder(buyerId, productId3);
        testFixtures.breakReservedStock(productId1);
        Instant expiresAt = Instant.now().minus(Duration.ofHours(1));
        testFixtures.setOrderExpired(order1.orderId(), expiresAt);
        testFixtures.setOrderExpired(order2.orderId(), expiresAt);
        testFixtures.setOrderExpired(order3.orderId(), expiresAt);

        // When
        int batchCount = orderExpirationService.expireBatch();

        // Then
        assertThat(batchCount).isEqualTo(2);

        Map<String, Object> o1 = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", order1.orderId());
        assertThat(o1.get("status")).isEqualTo("PENDING_PAYMENT");

        Map<String, Object> o2 = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", order2.orderId());
        assertThat(o2.get("status")).isEqualTo("EXPIRED");

        Map<String, Object> o3 = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", order3.orderId());
        assertThat(o3.get("status")).isEqualTo("EXPIRED");
    }
}
