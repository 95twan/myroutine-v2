package com.myroutine.order.application;

import com.myroutine.support.Behavior;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OrderSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

class RefundRecoveryServiceTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final RefundRecoveryService refundRecoveryService;
    private final CancelOrderLineService cancelOrderLineService;

    @MockitoSpyBean
    private RefundCompleter refundCompleter;

    @Autowired
    public RefundRecoveryServiceTest(TestFixtures testFixtures, RefundRecoveryService refundRecoveryService, CancelOrderLineService cancelOrderLineService) {
        this.testFixtures = testFixtures;
        this.refundRecoveryService = refundRecoveryService;
        this.cancelOrderLineService = cancelOrderLineService;
    }

    @Test
    @DisplayName("완료 직전에 실패해 PG_CANCELLED로 남은 환불을 복구하면 COMPLETED가 되고 환불액과 재고 복구는 한 번만 반영된다.")
    void recoverRefundFailedBeforeComplete() {
        // Given
        willThrow(new RuntimeException("test"))
                .willCallRealMethod()
                .given(refundCompleter)
                .complete(any());

        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        // When & Then

        assertThatThrownBy(() -> cancelOrderLineService.cancel(buyerId, orderId, lineId, "단순 변심"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test");

        UUID refundId = testFixtures.refundIdOf(lineId);

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("PG_CANCELLED");
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(1);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(1000);
        assertThat(snapshot.line(product1Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(0);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(1);

        testFixtures.setRefundUpdatedAt(refundId, Instant.now().minus(Duration.ofMinutes(6)));

        int count = refundRecoveryService.recoverBatch();

        snapshot = testFixtures.snapshotOf(orderId);

        assertThat(count).isEqualTo(1);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("COMPLETED");
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(1);
        assertThat(snapshot.line(product1Id).status()).isEqualTo("CANCELLED");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(1000);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(10);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(0);
    }

    @Test
    @DisplayName("취소 타임아웃으로 APPROVED에 멈춘 환불을 복구하면 같은 키로 PG를 다시 불러도 cancelled_amount는 한 번만 증가한다.")
    void recoverRefundStuckInApproved() {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        // When & Then
        cancelOrderLineService.cancel(buyerId, orderId, lineId, "단순 변심");

        UUID refundId = testFixtures.refundIdOf(lineId);

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("APPROVED");
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(1);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(0);
        assertThat(snapshot.line(product1Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(0);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(1);

        testFixtures.setRefundUpdatedAt(refundId, Instant.now().minus(Duration.ofMinutes(6)));

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(0));

        int count = refundRecoveryService.recoverBatch();

        snapshot = testFixtures.snapshotOf(orderId);

        assertThat(count).isEqualTo(1);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("COMPLETED");
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(2);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(1000);
        assertThat(snapshot.line(product1Id).status()).isEqualTo("CANCELLED");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(1000);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(10);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(0);
    }
}
