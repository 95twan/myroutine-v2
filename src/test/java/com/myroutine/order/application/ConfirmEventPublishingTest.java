package com.myroutine.order.application;

import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConfirmEventPublishingTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final AutoConfirmService autoConfirmService;

    @MockitoSpyBean
    private OrderEventPublisher publisher;

    @Autowired
    public ConfirmEventPublishingTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            OutboxAssert outboxAssert,
            AutoConfirmService autoConfirmService
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.autoConfirmService = autoConfirmService;
    }

    @Test
    @DisplayName("수동 구매확정은 확정한 품목 하나의 이벤트를 남긴다.")
    void confirmPublishesLineConfirmed() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/lines/{lineId}/confirm", orderId, line1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_LINE_CONFIRMED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(rows.get(0).payload().get("orderLineId").asString()).isEqualTo(line1Id.toString());
        assertThat(rows.get(0).payload().get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(rows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(rows.get(0).payload().get("productId").asString()).isEqualTo(product1Id.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(buyerId.toString());
        assertThat(rows.get(0).payload().get("amount").asLong()).isEqualTo(1000);
        Instant eventConfirmedAt = Instant.parse(rows.get(0).payload().get("confirmedAt").asString()).truncatedTo(ChronoUnit.MILLIS);
        Instant dbConfirmedAt = testFixtures.snapshotOf(orderId).line(product1Id).confirmedAt().truncatedTo(ChronoUnit.MILLIS);
        assertThat(eventConfirmedAt).isEqualTo(dbConfirmedAt);
    }

    @Test
    @DisplayName("구매확정 이벤트의 금액은 환불을 뺀 값이다.")
    void confirmPublishesAmountMinusRefunded() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);

        testFixtures.setRefundedAmount(line1Id, 300);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/lines/{lineId}/confirm", orderId, line1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_LINE_CONFIRMED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(rows.get(0).payload().get("amount").asLong()).isEqualTo(700);
    }

    @Test
    @DisplayName("자동 구매확정은 품목마다 이벤트를 남긴다.")
    void autoConfirmPublishesPerLine() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");

        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id, product3Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);
        UUID line2Id = testFixtures.orderLineIdOf(orderId, product2Id);
        UUID line3Id = testFixtures.orderLineIdOf(orderId, product3Id);

        testFixtures.setShopOrderDeliveredAt(shopOrderId, Instant.now().minus(Duration.ofDays(8)));

        outboxAssert.clear();

        // When & Then
        int count = autoConfirmService.confirmBatch();

        assertThat(count).isEqualTo(3);
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_LINE_CONFIRMED);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(OutboxSnapshot::aggregateId).containsOnly(orderId);
        assertThat(rows).extracting(row -> row.payload().get("orderLineId").asString()).containsExactlyInAnyOrder(line1Id.toString(), line2Id.toString(), line3Id.toString());
        assertThat(rows).extracting(row -> row.payload().get("amount").asLong()).containsExactlyInAnyOrder(1000L, 2000L, 3000L);

        count = autoConfirmService.confirmBatch();

        assertThat(count).isEqualTo(0);
        assertThat(outboxAssert.rows(OrderTopics.ORDER_LINE_CONFIRMED)).hasSize(3);
    }

    @Test
    @DisplayName("이벤트 발행이 실패하면 구매확정도 취소된다.")
    void publishFailureRollsBackConfirm() throws Exception {
        // Given
        willThrow(new IllegalStateException("test"))
                .given(publisher)
                .lineConfirmed(any(), any(), any());
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/lines/{lineId}/confirm", orderId, line1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        String status = testFixtures.snapshotOf(orderId).line(product1Id).status();
        assertThat(status).isEqualTo("DELIVERED");
        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }
}
