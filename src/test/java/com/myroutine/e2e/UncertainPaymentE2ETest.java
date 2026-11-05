package com.myroutine.e2e;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.order.application.OrderReconcileService;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UncertainPaymentE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final OrderReconcileService orderReconcileService;
    private final Clock clock;

    @Autowired
    public UncertainPaymentE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                                   OutboxAssert outboxAssert, OrderReconcileService orderReconcileService, Clock clock) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.orderReconcileService = orderReconcileService;
        this.clock = clock;
    }

    @Test
    @DisplayName("승인됐지만 응답이 늦은 결제는 대사로 주문이 PAID가 된다.")
    void lateApprovalResolvedToPaid() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 10000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        pg.on("/v1/payments/confirm", Behavior.delay(1000));

        // 1. 체크아웃 후 승인 타임아웃
        UUID orderId = checkoutAndConfirmTimesOut(buyerId, buyerToken, productId);

        // 2. 대사
        testFixtures.setOrderUpdatedAt(orderId, clock.instant().minus(Duration.ofMinutes(1)));
        assertThat(orderReconcileService.reconcileBatch()).isEqualTo(1);

        // 3. 주문과 이벤트
        assertThat(testFixtures.snapshotOf(orderId).status()).isEqualTo("PAID");
        assertThat(outboxAssert.rows(OrderTopics.ORDER_PAID)).hasSize(1);

        // 4. 불변식
        invariantChecker.checkAll();
    }

    @Test
    @DisplayName("승인 기록이 없는 결제는 대사로 주문이 PAYMENT_FAILED가 되고 장바구니는 그대로다.")
    void noApprovalRecordResolvedToPaymentFailed() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 10000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        pg.on("/v1/payments/confirm", Behavior.drop());

        // 1. 체크아웃 후 승인 타임아웃
        UUID orderId = checkoutAndConfirmTimesOut(buyerId, buyerToken, productId);

        // 2. 대사
        testFixtures.setOrderUpdatedAt(orderId, clock.instant().minus(Duration.ofMinutes(1)));
        assertThat(orderReconcileService.reconcileBatch()).isEqualTo(1);

        // 3. 주문·장바구니·이벤트
        assertThat(testFixtures.snapshotOf(orderId).status()).isEqualTo("PAYMENT_FAILED");
        assertThat(testFixtures.cartSnapshotOf(buyerId).quantity(productId)).isEqualTo(1);
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_EXPIRED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).payload().get("reason").asText()).isEqualTo("PAYMENT_FAILED");

        // 4. 불변식
        invariantChecker.checkAll();
    }

    private UUID checkoutAndConfirmTimesOut(UUID buyerId, String buyerToken, UUID productId) throws Exception {
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);
        String checkoutBody = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"%s\"],\"addressId\":\"%s\"}".formatted(cartItemId, addressId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(checkoutBody, "$.orderId"));
        String pgOrderId = JsonPath.read(checkoutBody, "$.pgOrderId");

        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"pk_s3_%s\",\"pgOrderId\":\"%s\",\"amount\":10000}".formatted(orderId, pgOrderId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_IN_PROGRESS"));
        return orderId;
    }
}
