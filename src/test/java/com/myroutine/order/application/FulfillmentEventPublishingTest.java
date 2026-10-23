package com.myroutine.order.application;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.order.web.CancelLineRequest;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FulfillmentEventPublishingTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final ObjectMapper objectMapper;
    private final RefundCompleter refundCompleter;
    private final RefundRecoveryService refundRecoveryService;

    @Autowired
    public FulfillmentEventPublishingTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            OutboxAssert outboxAssert,
            ObjectMapper objectMapper,
            RefundCompleter refundCompleter,
            RefundRecoveryService refundRecoveryService
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.objectMapper = objectMapper;
        this.refundCompleter = refundCompleter;
        this.refundRecoveryService = refundRecoveryService;
    }

    @Test
    @DisplayName("발송과 배송완료는 orderId를 키로 이벤트를 남긴다.")
    void shipAndDeliverPublishWithOrderIdKey() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        outboxAssert.clear();

        // When
        testFixtures.shipShopOrder(sellerId, shopId, shopOrderId);

        // Then
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.SHOP_ORDER_SHIPPED);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(rows.get(0).payload().get("shopOrderId").asString()).isEqualTo(shopOrderId.toString());
        assertThat(rows.get(0).payload().get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(buyerId.toString());
        assertThat(rows.get(0).payload().get("carrier").asString()).isEqualTo("CJ대한통운");
        assertThat(rows.get(0).payload().get("trackingNumber").asString()).isEqualTo("1234567890");

        testFixtures.deliverShopOrder(sellerId, shopId, shopOrderId);

        rows = outboxAssert.rows(OrderTopics.SHOP_ORDER_DELIVERED);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(rows.get(0).payload().get("shopOrderId").asString()).isEqualTo(shopOrderId.toString());
        assertThat(rows.get(0).payload().get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(buyerId.toString());
    }

    @Test
    @DisplayName("환불이 완료되면 order-line-refunded 이벤트를 한 번만 남긴다.")
    void refundCompletePublishesRefundedOnce() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        outboxAssert.clear();

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/orders/{id}/lines/{lineId}/cancel", orderId, line1Id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String refundId = JsonPath.read(result.getResponse().getContentAsString(), "$.refundId");

        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_LINE_REFUNDED);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(rows.get(0).payload().get("refundId").asString()).isEqualTo(refundId);
        assertThat(rows.get(0).payload().get("orderLineId").asString()).isEqualTo(line1Id.toString());
        assertThat(rows.get(0).payload().get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(buyerId.toString());
        assertThat(rows.get(0).payload().get("amount").asLong()).isEqualTo(1000);

        refundCompleter.complete(UUID.fromString(refundId));
        assertThat(outboxAssert.rows(OrderTopics.ORDER_LINE_REFUNDED)).hasSize(1);
    }

    @Test
    @DisplayName("환불이 끝나기 전에는 이벤트를 남기지 않고 복구 뒤에 남긴다.")
    void refundPublishesOnlyAfterCompletion() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        outboxAssert.clear();

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/orders/{id}/lines/{lineId}/cancel", orderId, line1Id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();

        String refundId = JsonPath.read(result.getResponse().getContentAsString(), "$.refundId");

        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_LINE_REFUNDED);
        assertThat(rows).hasSize(0);

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(0));
        testFixtures.setRefundUpdatedAt(UUID.fromString(refundId), Instant.now().minus(Duration.ofMinutes(6)));

        int count = refundRecoveryService.recoverBatch();

        assertThat(count).isEqualTo(1);

        rows = outboxAssert.rows(OrderTopics.ORDER_LINE_REFUNDED);
        assertThat(rows).hasSize(1);
    }
}
