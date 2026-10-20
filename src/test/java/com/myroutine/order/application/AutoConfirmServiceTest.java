package com.myroutine.order.application;

import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OrderSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfirmServiceTest extends IntegrationTestSupport {
    private final TestFixtures testFixtures;
    private final AutoConfirmService autoConfirmService;
    private final ReturnService returnService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AutoConfirmServiceTest(TestFixtures testFixtures, AutoConfirmService autoConfirmService, ReturnService returnService, JdbcTemplate jdbcTemplate) {
        this.testFixtures = testFixtures;
        this.autoConfirmService = autoConfirmService;
        this.returnService = returnService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("배송완료 8일 지난 주문은 확정하고 6일 지난 주문은 그대로 둔다.")
    void confirmBatchConfirmsOnlyOrdersDeliveredOverSevenDaysAgo() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);
        UUID order2Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID shopOrder1Id = testFixtures.shopOrderIdOf(order1Id, shopId);
        UUID shopOrder2Id = testFixtures.shopOrderIdOf(order2Id, shopId);

        testFixtures.setShopOrderDeliveredAt(shopOrder1Id, Instant.now().minus(Duration.ofDays(8)));
        testFixtures.setShopOrderDeliveredAt(shopOrder2Id, Instant.now().minus(Duration.ofDays(6)));

        // When
        int count = autoConfirmService.confirmBatch();

        // Then
        assertThat(count).isEqualTo(1);

        OrderSnapshot s1 = testFixtures.snapshotOf(order1Id);
        assertThat(s1.line(product1Id).status()).isEqualTo("CONFIRMED");
        assertThat(s1.line(product1Id).confirmedAt()).isNotNull();
        assertThat(s1.shopOrderStatus()).isEqualTo("COMPLETED");

        OrderSnapshot s2 = testFixtures.snapshotOf(order2Id);
        assertThat(s2.line(product1Id).status()).isEqualTo("DELIVERED");
        assertThat(s2.line(product1Id).confirmedAt()).isNull();
        assertThat(s2.shopOrderStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("반품 요청 중인 품목은 확정하지 않고 나머지 품목만 확정한다.")
    void confirmBatchSkipsReturnRequestedLine() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id);

        UUID shopOrder1Id = testFixtures.shopOrderIdOf(order1Id, shopId);
        UUID line1Id = testFixtures.orderLineIdOf(order1Id, product1Id);

        testFixtures.setShopOrderDeliveredAt(shopOrder1Id, Instant.now().minus(Duration.ofDays(8)));

        returnService.request(buyerId, order1Id, line1Id, "단순 변심");

        // When
        int count = autoConfirmService.confirmBatch();

        // Then
        assertThat(count).isEqualTo(1);

        OrderSnapshot s1 = testFixtures.snapshotOf(order1Id);
        assertThat(s1.line(product1Id).status()).isEqualTo("RETURN_REQUESTED");
        assertThat(s1.line(product1Id).confirmedAt()).isNull();
        assertThat(s1.line(product2Id).status()).isEqualTo("CONFIRMED");
        assertThat(s1.line(product2Id).confirmedAt()).isNotNull();
        assertThat(s1.shopOrderStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("대상이 100건을 넘어도 150건을 모두 확정한다.")
    void confirmBatchConfirmsMoreThanOnePage() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");

        for (int i = 0; i < 150; i++) {
            UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);
            UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
            testFixtures.setShopOrderDeliveredAt(shopOrderId, Instant.now().minus(Duration.ofDays(8)));
        }

        // When
        int count = autoConfirmService.confirmBatch();

        // Then
        assertThat(count).isEqualTo(150);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.order_line WHERE status = 'CONFIRMED'", Integer.class)).isEqualTo(150);
    }
}
