package com.myroutine.order.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CancelOrderLineTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    public CancelOrderLineTest(TestFixtures testFixtures, MockMvc mockMvc, ObjectMapper objectMapper) {
        this.testFixtures = testFixtures;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("품목 3개 중 1개를 취소하면 그 품목 금액만 환불되고 나머지 품목은 PAID 그대로다.")
    void cancelOneOfThreeLinesRefundsOnlyThatLine() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1000));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.line(product1Id).status()).isEqualTo("CANCELLED");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(1000L);
        assertThat(snapshot.line(product2Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product2Id).refundedAmount()).isEqualTo(0L);
        assertThat(snapshot.line(product3Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product3Id).refundedAmount()).isEqualTo(0L);
        assertThat(snapshot.shopOrderStatus()).isEqualTo("PAID");

        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(10);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(0);
        assertThat(testFixtures.stockOf(product2Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product2Id).sold()).isEqualTo(1);
        assertThat(testFixtures.stockOf(product3Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product3Id).sold()).isEqualTo(1);
    }

    @Test
    @DisplayName("품목을 취소하면 가짜 서버 취소 요청의 cancelAmount는 품목 금액이고 Idempotency-Key는 refundId다.")
    void cancelSendsLineAmountAndRefundIdAsIdempotencyKey() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andReturn();

        String refundId = JsonPath.read(result.getResponse().getContentAsString(), "$.refundId");

        List<RecordedRequest> cancels = pg.requests("/v1/payments/").stream()
                .filter(r -> r.path().endsWith("/cancel"))
                .toList();

        assertThat(cancels).hasSize(1);
        RecordedRequest cancel = cancels.get(0);

        assertThat(cancel.headers().get("idempotency-key")).isEqualTo(refundId);
        assertThat(objectMapper.readTree(cancel.body()).get("cancelAmount").asLong()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("품목 두 개를 차례로 취소하면 refunded_amount와 payment cancelled_amount가 정확하고 승인액을 넘지 않는다.")
    void cancelTwoLinesInSequenceKeepsAmountsConsistent() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID line1Id = testFixtures.orderLineIdOf(orderId, product1Id);
        UUID line2Id = testFixtures.orderLineIdOf(orderId, product2Id);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, line1Id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1000));

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, line2Id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(2000));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.line(product1Id).status()).isEqualTo("CANCELLED");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(1000L);
        assertThat(snapshot.line(product2Id).status()).isEqualTo("CANCELLED");
        assertThat(snapshot.line(product2Id).refundedAmount()).isEqualTo(2000L);
        assertThat(snapshot.line(product3Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product3Id).refundedAmount()).isEqualTo(0L);
        assertThat(snapshot.shopOrderStatus()).isEqualTo("PAID");
        assertThat(snapshot.refundStatuses()).containsExactly("COMPLETED", "COMPLETED");
        assertThat(snapshot.payment().amount()).isEqualTo(6000L);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(3000L);
        assertThat(snapshot.payment().status()).isEqualTo("PARTIAL_CANCELLED");
    }

    @Test
    @DisplayName("발송된 품목 취소를 실패한다. (INVALID_STATE_TRANSITION)")
    void cancelShippedLineFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        testFixtures.shipShopOrder(sellerId, shopId, shopOrderId);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.refundStatuses()).isEmpty();
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(0L);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(1);
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).isEmpty();
    }

    @Test
    @DisplayName("남의 주문의 품목 취소를 실패한다. (ORDER_NOT_FOUND)")
    void cancelOthersOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        String token = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.refundStatuses()).isEmpty();
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).isEmpty();
        assertThat(snapshot.line(product1Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("PG 취소가 타임아웃되면 202를 응답하고 환불은 APPROVED로 남는다.")
    void cancelPgTimeoutReturnsAcceptedAndRefundStaysApproved() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(1000));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.refundStatuses()).hasSize(1);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("APPROVED");
        assertThat(snapshot.line(product1Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(0L);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(1);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(0);
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(1);
    }

    @Test
    @DisplayName("PG 취소가 거절되면 502를 응답하고 환불은 FAILED가 되며 재고·품목은 변하지 않는다.")
    void cancelPgRejectedReturnsBadGatewayAndRefundFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        pg.on("/v1/payments/" + paymentKey, Behavior.reject("NOT_CANCELABLE_AMOUNT"));

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PG_ERROR"));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.refundStatuses()).hasSize(1);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("FAILED");
        assertThat(snapshot.line(product1Id).status()).isEqualTo("PAID");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(0L);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(1);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(0);
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(1);
    }

    @Test
    @DisplayName("진행 중 환불이 있는 가게주문의 발송을 실패한다. (REFUND_IN_PROGRESS)")
    void shipFailsWhenRefundInProgress() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // When & Then
        assertThatThrownBy(() -> testFixtures.shipShopOrder(sellerId, shopId, shopOrderId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.REFUND_IN_PROGRESS));

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);

        assertThat(snapshot.shopOrderStatus()).isEqualTo("PAID");
    }
}
