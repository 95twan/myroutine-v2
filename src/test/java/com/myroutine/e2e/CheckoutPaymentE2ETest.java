package com.myroutine.e2e;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.config.BusinessProperties;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.notification.application.NotificationDispatcher;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.settlement.api.event.SettlementTopics;
import com.myroutine.settlement.application.SettlementRunResult;
import com.myroutine.settlement.application.SettlementService;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CheckoutPaymentE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final FakePayoutGateway fakePayoutGateway;
    private final OutboxRelay outboxRelay;
    private final SettlementService settlementService;
    private final NotificationDispatcher notificationDispatcher;
    private final BusinessProperties businessProperties;

    @Autowired
    public CheckoutPaymentE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                                  OutboxAssert outboxAssert, FakePayoutGateway fakePayoutGateway, OutboxRelay outboxRelay,
                                  SettlementService settlementService, NotificationDispatcher notificationDispatcher,
                                  BusinessProperties businessProperties) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.fakePayoutGateway = fakePayoutGateway;
        this.outboxRelay = outboxRelay;
        this.settlementService = settlementService;
        this.notificationDispatcher = notificationDispatcher;
        this.businessProperties = businessProperties;
    }

    @Test
    @DisplayName("가게 2곳 상품을 체크아웃·결제·발송·배송완료·구매확정하면 가게별로 정산·지급되고 알림이 모두 발송된다.")
    void checkoutToSettlementPayout() throws Exception {
        // Given
        UUID sellerAId = testFixtures.signup("seller-a@test.com");
        UUID sellerBId = testFixtures.signup("seller-b@test.com");
        UUID shopAId = testFixtures.openShop(sellerAId, "1111111111");
        UUID shopBId = testFixtures.openShop(sellerBId, "2222222222");
        UUID productA1Id = testFixtures.registerProduct(sellerAId, shopAId, 10000, 10);
        UUID productA2Id = testFixtures.registerProduct(sellerAId, shopAId, 5000, 10);
        UUID productB1Id = testFixtures.registerProduct(sellerBId, shopBId, 30000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        String sellerAToken = testFixtures.login("seller-a@test.com");
        String sellerBToken = testFixtures.login("seller-b@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItem1Id = testFixtures.addToCart(buyerId, productA1Id, 1);
        UUID cartItem2Id = testFixtures.addToCart(buyerId, productA2Id, 2);
        UUID cartItem3Id = testFixtures.addToCart(buyerId, productB1Id, 1);

        // 1. 체크아웃
        String checkoutBody = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"%s\",\"%s\",\"%s\"],\"addressId\":\"%s\"}".formatted(cartItem1Id, cartItem2Id, cartItem3Id, addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(50000))
                .andExpect(jsonPath("$.shopOrders.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(checkoutBody, "$.orderId"));
        String pgOrderId = JsonPath.read(checkoutBody, "$.pgOrderId");

        // 2. 결제 승인
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"pk_s1_%s\",\"pgOrderId\":\"%s\",\"amount\":50000}".formatted(orderId, pgOrderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(testFixtures.snapshotOf(orderId).shopOrderStatuses()).containsExactly("PAID", "PAID");
        assertThat(testFixtures.cartSnapshotOf(buyerId).itemCount()).isZero();

        // 3. outbox와 알림
        assertThat(outboxAssert.rows(OrderTopics.ORDER_PAID)).hasSize(1);
        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> {
            NotificationCounts counts = testFixtures.notificationCounts();
            assertThat(counts.type("ORDER_PAID")).isEqualTo(1);
            assertThat(counts.type("SHOP_ORDER_RECEIVED")).isEqualTo(2);
        });
        notificationDispatcher.dispatchBatch();

        // 4. 발송·배송완료
        shipAndDeliver(sellerAToken, shopAId, testFixtures.shopOrderIdOf(orderId, shopAId));
        shipAndDeliver(sellerBToken, shopBId, testFixtures.shopOrderIdOf(orderId, shopBId));
        assertThat(testFixtures.snapshotOf(orderId).shopOrderStatuses()).containsExactly("DELIVERED", "DELIVERED");
        assertThat(outboxAssert.rows(OrderTopics.SHOP_ORDER_SHIPPED)).hasSize(2);
        assertThat(outboxAssert.rows(OrderTopics.SHOP_ORDER_DELIVERED)).hasSize(2);

        // 5. 구매확정
        for (UUID productId : List.of(productA1Id, productA2Id, productB1Id)) {
            mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", orderId, testFixtures.orderLineIdOf(orderId, productId))
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk());
        }
        OrderSnapshot confirmed = testFixtures.snapshotOf(orderId);
        assertThat(confirmed.shopOrderStatuses()).containsExactly("COMPLETED", "COMPLETED");
        assertThat(outboxAssert.rows(OrderTopics.ORDER_LINE_CONFIRMED)).hasSize(3);
        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> assertThat(testFixtures.unsettledItemCount()).isEqualTo(3));
        assertThat(testFixtures.settlementItemSnapshotOf(testFixtures.orderLineIdOf(orderId, productA1Id)).amount()).isEqualTo(10000);
        assertThat(testFixtures.settlementItemSnapshotOf(testFixtures.orderLineIdOf(orderId, productA2Id)).amount()).isEqualTo(10000);
        assertThat(testFixtures.settlementItemSnapshotOf(testFixtures.orderLineIdOf(orderId, productB1Id)).amount()).isEqualTo(30000);

        // 6. 정산
        YearMonth month = YearMonth.from(confirmed.line(productA1Id).confirmedAt().atZone(businessProperties.businessZone()));
        SettlementRunResult result = settlementService.run(month);
        assertThat(result).isEqualTo(new SettlementRunResult(2, 2, 0));
        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(2);
        SettlementSnapshot settlementA = testFixtures.settlementSnapshotOf(shopAId, month);
        assertThat(settlementA.grossAmount()).isEqualTo(20000);
        assertThat(settlementA.itemCount()).isEqualTo(2);
        assertThat(settlementA.status()).isEqualTo("PAID");
        SettlementSnapshot settlementB = testFixtures.settlementSnapshotOf(shopBId, month);
        assertThat(settlementB.grossAmount()).isEqualTo(30000);
        assertThat(settlementB.itemCount()).isEqualTo(1);
        assertThat(settlementB.status()).isEqualTo("PAID");
        assertThat(testFixtures.unsettledItemCount()).isZero();
        List<PayoutCall> payouts = fakePayoutGateway.calls();
        assertThat(payouts).hasSize(2);
        assertThat(payouts.get(0).amount()).isEqualTo(19000);
        assertThat(payouts.get(1).amount()).isEqualTo(28500);

        // 7. 지급 알림
        assertThat(outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID)).hasSize(2);
        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> {
            NotificationCounts counts = testFixtures.notificationCounts();
            assertThat(counts.type("WELCOME")).isEqualTo(3);
            assertThat(counts.type("SHIPPED")).isEqualTo(2);
            assertThat(counts.type("DELIVERED")).isEqualTo(2);
            assertThat(counts.type("SETTLEMENT_PAID")).isEqualTo(2);
        });
        notificationDispatcher.dispatchBatch();
        assertThat(testFixtures.notificationCounts().status("SENT")).isEqualTo(12);
        assertThat(testFixtures.unsentOutboxCount()).isZero();

        // 8. 불변식
        invariantChecker.checkAll();
    }

    private void shipAndDeliver(String sellerToken, UUID shopId, UUID shopOrderId) throws Exception {
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"1234567890\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/deliver", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());
    }
}
