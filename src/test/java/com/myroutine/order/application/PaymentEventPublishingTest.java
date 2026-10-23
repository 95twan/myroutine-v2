package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.order.api.event.OrderPaidEvent;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.order.api.event.PaidLine;
import com.myroutine.order.api.event.PaidShopOrder;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.payment.api.PaymentState;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentEventPublishingTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final ObjectMapper objectMapper;
    private final OrderPaymentApplier orderPaymentApplier;
    private final OrderReconcileService orderReconcileService;
    private final OrderExpirationService orderExpirationService;

    @Autowired
    public PaymentEventPublishingTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            OutboxAssert outboxAssert,
            ObjectMapper objectMapper,
            OrderPaymentApplier orderPaymentApplier,
            OrderReconcileService orderReconcileService,
            OrderExpirationService orderExpirationService
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.objectMapper = objectMapper;
        this.orderPaymentApplier = orderPaymentApplier;
        this.orderReconcileService = orderReconcileService;
        this.orderExpirationService = orderExpirationService;
    }

    @Test
    @DisplayName("결제가 완료되면 order-paid 이벤트를 한 번만 남긴다.")
    void paymentApprovedPublishesOrderPaidOnce() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id, product2Id);

        outboxAssert.clear();

        // When
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());

        // Then
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_PAID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateType()).isEqualTo("ORDER");
        assertThat(rows.get(0).aggregateId()).isEqualTo(order.orderId());

        UUID shopOrderId = testFixtures.shopOrderIdOf(order.orderId(), shopId);
        UUID line1Id = testFixtures.orderLineIdOf(order.orderId(), product1Id);
        UUID line2Id = testFixtures.orderLineIdOf(order.orderId(), product2Id);

        OrderPaidEvent event = objectMapper.treeToValue(rows.get(0).payload(), OrderPaidEvent.class);
        assertThat(event.orderId()).isEqualTo(order.orderId());
        assertThat(event.memberId()).isEqualTo(buyerId);
        assertThat(event.totalAmount()).isEqualTo(3000);

        assertThat(event.shopOrders()).hasSize(1);
        PaidShopOrder shopOrder = event.shopOrders().get(0);
        assertThat(shopOrder.shopOrderId()).isEqualTo(shopOrderId);
        assertThat(shopOrder.shopId()).isEqualTo(shopId);
        assertThat(shopOrder.lines())
                .extracting(PaidLine::orderLineId, PaidLine::productId, PaidLine::productName, PaidLine::quantity)
                .containsExactlyInAnyOrder(
                        tuple(line1Id, product1Id, "product", 1),
                        tuple(line2Id, product2Id, "product", 1));

        OrderStatus status = orderPaymentApplier.applyPaymentState(order.orderId(), PaymentState.APPROVED, null);
        assertThat(status).isEqualTo(OrderStatus.PAID);
        assertThat(outboxAssert.rows(OrderTopics.ORDER_PAID)).hasSize(1);
    }

    @Test
    @DisplayName("대사로 늦게 확정된 결제도 order-paid 이벤트를 남긴다.")
    void reconcilePublishesOrderPaid() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));

        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());
        testFixtures.setOrderUpdatedAt(order.orderId(), Instant.now().minusSeconds(60));

        outboxAssert.clear();

        // When & Then
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_PAID);
        assertThat(rows).hasSize(0);

        int count = orderReconcileService.reconcileBatch();

        assertThat(count).isEqualTo(1);
        rows = outboxAssert.rows(OrderTopics.ORDER_PAID);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateType()).isEqualTo("ORDER");
        assertThat(rows.get(0).aggregateId()).isEqualTo(order.orderId());
        assertThat(rows.get(0).payload().get("totalAmount").asLong()).isEqualTo(1000);
    }

    @Test
    @DisplayName("주문이 만료되면 reason EXPIRED로 order-expired 이벤트를 남긴다.")
    void expirePublishesOrderExpired() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));

        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        testFixtures.setOrderExpired(order.orderId(), Instant.now().minusSeconds(60));

        outboxAssert.clear();

        // When & Then
        int count = orderExpirationService.expireBatch();

        assertThat(count).isEqualTo(1);
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_EXPIRED);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateId()).isEqualTo(order.orderId());
        assertThat(rows.get(0).payload().get("orderId").asString()).isEqualTo(order.orderId().toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(buyerId.toString());
        assertThat(rows.get(0).payload().get("reason").asString()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("결제가 실패하면 reason PAYMENT_FAILED로 order-expired 이벤트를 남긴다.")
    void paymentFailurePublishesOrderExpired() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"));

        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);

        outboxAssert.clear();

        // When & Then
        assertThatThrownBy(() -> testFixtures.confirmPayment(buyerId, order.orderId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.PAYMENT_FAILED));

        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_EXPIRED);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateId()).isEqualTo(order.orderId());
        assertThat(rows.get(0).payload().get("reason").asString()).isEqualTo("PAYMENT_FAILED");

        assertThat(outboxAssert.rows(OrderTopics.ORDER_PAID)).hasSize(0);
    }

    @Test
    @DisplayName("결제 전 주문 취소는 이벤트를 남기지 않는다.")
    void cancelPendingOrderPublishesNothing() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");

        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }
}
