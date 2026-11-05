package com.myroutine.order.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminRefundRetryTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdminRefundRetryTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("FAILED 환불을 재시도하면 COMPLETED가 된다.")
    void retryFailedRefundCompletes(CapturedOutput output) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        pg.on("/v1/payments/" + paymentKey, Behavior.approve());

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", refundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refundId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        RefundRow refund = testFixtures.refundRowOf(refundId);
        assertThat(refund.status()).isEqualTo("COMPLETED");
        assertThat(refund.pgAttempts()).isEqualTo(1);

        List<RecordedRequest> cancels = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(cancels).hasSize(2);
        assertThat(cancels.get(1).headers().get("idempotency-key")).isEqualTo(refundId + "-r1");

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.line(product2Id).status()).isEqualTo("PAID");
        assertThat(s.line(product2Id).refundedAmount()).isEqualTo(0);
        assertThat(s.line(product3Id).status()).isEqualTo("PAID");
        assertThat(s.line(product3Id).refundedAmount()).isEqualTo(0);
        assertThat(output.getAll()).contains("target=" + refundId + " by=" + adminId);
    }

    @Test
    @DisplayName("같은 환불의 재시도를 한 번 더 요청하면 실패한다. (FAILED가 아님)")
    void retryRefundTwiceFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        pg.on("/v1/payments/" + paymentKey, Behavior.approve());
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", refundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", refundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        RefundRow refund = testFixtures.refundRowOf(refundId);
        assertThat(refund.status()).isEqualTo("COMPLETED");
        assertThat(refund.pgAttempts()).isEqualTo(1);
        assertThat(pg.requests("/v1/payments/" + paymentKey + "/cancel")).hasSize(2);
        assertThat(testFixtures.paymentCancelOf(refundId + "-r1").status()).isEqualTo("DONE");
        assertThat(testFixtures.snapshotOf(orderId).payment().cancelledAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("FAILED가 아닌 환불의 재시도를 실패한다. (REQUESTED)")
    void retryNonFailedRefundFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        UUID refundId = requestReturn(buyerToken, orderId, lineId);

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", refundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        RefundRow refund = testFixtures.refundRowOf(refundId);
        assertThat(refund.status()).isEqualTo("REQUESTED");
        assertThat(refund.pgAttempts()).isEqualTo(0);
        assertThat(pg.requests("/v1/payments/" + paymentKey + "/cancel")).isEmpty();
    }

    @Test
    @DisplayName("FAILED 환불 목록을 조회한다.")
    void listFailedRefunds() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        // When & Then
        mockMvc.perform(get("/admin/api/refunds")
                        .param("status", "FAILED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(refundId.toString()))
                .andExpect(jsonPath("$.items[0].orderLineId").value(lineId.toString()))
                .andExpect(jsonPath("$.items[0].amount").value(1000))
                .andExpect(jsonPath("$.items[0].failureCode").value("NOT_CANCELABLE_AMOUNT"))
                .andExpect(jsonPath("$.items[0].pgAttempts").value(0))
                .andExpect(jsonPath("$.items[0].productName").value(notNullValue()));
    }

    @Test
    @DisplayName("재시도가 다시 거절되면 502를 돌려주고 환불은 FAILED로 남는다.")
    void retryFailsAgainWithBadGateway() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", refundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PG_ERROR"));

        RefundRow refund = testFixtures.refundRowOf(refundId);
        assertThat(refund.status()).isEqualTo("FAILED");
        assertThat(refund.pgAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도한 PG 취소 결과가 불확실하면 202를 돌려주고 환불은 APPROVED로 남는다.")
    void retryWithUncertainPgResultReturnsAccepted() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", refundId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(testFixtures.refundRowOf(refundId).status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("없는 환불의 재시도를 실패한다. (존재하지 않는 환불)")
    void retryUnknownRefundFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/retry", Ids.newId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_FOUND"));
    }

    @Test
    @DisplayName("FAILED 구매자 취소 환불을 종결하면 REJECTED가 되고 품목은 PAID로 남는다.")
    void closeFailedBuyerCancelRefund(CapturedOutput output) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/close", refundId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PG에서 이미 취소됨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refundId.toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        RefundRow refund = testFixtures.refundRowOf(refundId);
        assertThat(refund.status()).isEqualTo("REJECTED");
        assertThat(refund.rejectReason()).isEqualTo("PG에서 이미 취소됨");

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.line(product1Id).status()).isEqualTo("PAID");
        assertThat(s.line(product1Id).refundedAmount()).isEqualTo(0);
        assertThat(pg.requests("/v1/payments/" + paymentKey + "/cancel")).hasSize(1);
        assertThat(output.getAll()).contains("target=" + refundId + " by=" + adminId);
    }

    @Test
    @DisplayName("FAILED 반품 환불을 종결하면 REJECTED가 되고 품목은 DELIVERED로 돌아간다.")
    void closeFailedReturnRefundRestoresLine() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        UUID refundId = requestReturn(buyerToken, orderId, lineId);
        pg.on("/v1/payments/" + paymentKey, Behavior.reject("NOT_CANCELABLE_AMOUNT"));
        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/approve", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isBadGateway());
        assertThat(testFixtures.refundRowOf(refundId).status()).isEqualTo("FAILED");

        // When & Then
        mockMvc.perform(post("/admin/api/refunds/{id}/close", refundId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PG에서 이미 취소됨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(testFixtures.snapshotOf(orderId).line(product1Id).status()).isEqualTo("DELIVERED");
        assertThat(testFixtures.refundRowOf(refundId).status()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("FAILED 환불이 있는 가게주문의 발송을 실패한다. (REFUND_IN_PROGRESS)")
    void shipShopOrderWithFailedRefundFails() throws Exception {
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
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipRequest("CJ 대한통운", "1234567890"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_IN_PROGRESS"));

        assertThat(testFixtures.snapshotOf(orderId).shopOrderStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("FAILED 환불이 있는 품목의 구매자 재취소를 실패한다. (DUPLICATE_RESOURCE)")
    void cancelLineWithFailedRefundFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        failBuyerCancel(buyerToken, orderId, lineId, paymentKey);
        UUID refundId = testFixtures.refundIdOf(lineId);

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelLineRequest("단순 변심"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        assertThat(pg.requests("/v1/payments/" + paymentKey + "/cancel")).hasSize(1);
        assertThat(testFixtures.refundRowOf(refundId).status()).isEqualTo("FAILED");
    }

    private void failBuyerCancel(String buyerToken, UUID orderId, UUID lineId, String paymentKey) throws Exception {
        pg.on("/v1/payments/" + paymentKey, Behavior.reject("NOT_CANCELABLE_AMOUNT"));
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelLineRequest("단순 변심"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PG_ERROR"));
    }

    private UUID requestReturn(String buyerToken, UUID orderId, UUID lineId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, lineId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReturnRequest("상품 불량"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));
    }
}
