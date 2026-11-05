package com.myroutine.e2e;

import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LineCancelE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final OutboxRelay outboxRelay;

    @Autowired
    public LineCancelE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                             OutboxAssert outboxAssert, OutboxRelay outboxRelay) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.outboxRelay = outboxRelay;
    }

    @Test
    @DisplayName("품목 3개 중 2개를 차례로 취소하면 누적 취소액이 맞고 남은 품목은 구매확정·정산 적재까지 정상 진행된다.")
    void cancelTwoLinesThenCompleteRemainingLine() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);
        UUID line2Id = testFixtures.orderLineIdOf(orderId, product2Id);
        UUID line3Id = testFixtures.orderLineIdOf(orderId, product3Id);

        // 1. 첫 취소
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, line1Id)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1000));

        // 2. 두 번째 취소
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, line2Id)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(2000));
        assertThat(testFixtures.snapshotOf(orderId).payment().cancelledAmount()).isEqualTo(3000);

        // 3. 남은 품목 정상 진행
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"1234567890\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/deliver", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", orderId, line3Id)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.line(product3Id).status()).isEqualTo("CONFIRMED");
        assertThat(s.line(product3Id).refundedAmount()).isZero();
        assertThat(s.shopOrderStatus()).isEqualTo("COMPLETED");

        // 4. 이벤트
        List<OutboxSnapshot> refunded = outboxAssert.rows(OrderTopics.ORDER_LINE_REFUNDED);
        assertThat(refunded).hasSize(2);
        assertThat(refunded.get(0).payload().get("amount").asText()).isEqualTo("1000");
        assertThat(refunded.get(1).payload().get("amount").asText()).isEqualTo("2000");
        List<OutboxSnapshot> confirmed = outboxAssert.rows(OrderTopics.ORDER_LINE_CONFIRMED);
        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).payload().get("amount").asText()).isEqualTo("3000");

        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> {
            assertThat(testFixtures.notificationCounts().type("REFUNDED")).isEqualTo(2);
            assertThat(testFixtures.countOf("settlement.settlement_item")).isEqualTo(1);
        });
        assertThat(testFixtures.settlementItemSnapshotOf(line3Id).amount()).isEqualTo(3000);

        // 5. 불변식
        invariantChecker.checkAll();
    }
}
