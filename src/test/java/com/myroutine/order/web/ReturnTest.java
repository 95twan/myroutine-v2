package com.myroutine.order.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReturnTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReturnTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("반품을 요청하고 판매자가 승인하면 환불이 완료되고 품목은 RETURNED, 재고는 복구된다.")
    void requestAndApproveReturnCompletesRefund() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        // When & Then
        ReturnRequest returnRequest = new ReturnRequest("상품 불량");
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andReturn();

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.refundStatuses()).containsExactly("REQUESTED");
        assertThat(s.line(product1Id).status()).isEqualTo("RETURN_REQUESTED");
        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));
        RefundRow r = testFixtures.refundRowOf(refundId);
        assertThat(r.reason()).isEqualTo("상품 불량");
        assertThat(r.amount()).isEqualTo(1000);
        assertThat(r.reasonType()).isEqualTo("RETURN");

        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/approve", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refundId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1000));

        s = testFixtures.snapshotOf(orderId);
        r = testFixtures.refundRowOf(refundId);

        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(s.line(product1Id).status()).isEqualTo("RETURNED");
        assertThat(s.line(product1Id).refundedAmount()).isEqualTo(1000);
        assertThat(s.line(product2Id).status()).isEqualTo("DELIVERED");
        assertThat(s.line(product2Id).refundedAmount()).isEqualTo(0);
        assertThat(s.shopOrderStatus()).isEqualTo("DELIVERED");

        assertThat(pg.requests("/v1/payments/").stream().filter(req -> req.path().endsWith("/cancel"))).hasSize(1);

        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(10);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(0);
        assertThat(testFixtures.stockOf(product2Id).available()).isEqualTo(9);
        assertThat(testFixtures.stockOf(product2Id).sold()).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 품목을 반품하면 가게주문이 COMPLETED가 되고 결제는 CANCELLED가 된다.")
    void returnLastLineCompletesShopOrder() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");

        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/approve", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        ShopOrderSnapshot so = testFixtures.shopOrderSnapshotOf(shopOrderId);

        assertThat(s.shopOrderStatus()).isEqualTo("COMPLETED");
        assertThat(s.payment().cancelledAmount()).isEqualTo(1000);

        assertThat(so.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("반품 요청을 판매자가 거절하면 품목은 DELIVERED로 돌아가고, 같은 품목의 재요청을 실패한다. (RETURN_NOT_ALLOWED)")
    void rejectReturnRestoresLineAndBlocksReRequest() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");

        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));

        RejectReturnRequest rejectReturnRequest = new RejectReturnRequest("사용 흔적 있음");

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/reject", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReturnRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        RefundRow r = testFixtures.refundRowOf(refundId);

        assertThat(r.status()).isEqualTo("REJECTED");
        assertThat(r.rejectReason()).isEqualTo("사용 흔적 있음");

        assertThat(s.line(product1Id).status()).isEqualTo("DELIVERED");

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_ALLOWED"));

        s = testFixtures.snapshotOf(orderId);
        assertThat(s.refundStatuses()).containsExactly("REJECTED");
    }

    @Test
    @DisplayName("배송 중 품목의 반품 요청을 실패한다. (INVALID_STATE_TRANSITION)")
    void requestReturnOfShippedLineFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        testFixtures.shipShopOrder(sellerId, shopId, shopOrderId);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.refundStatuses()).isEmpty();
        assertThat(s.line(product1Id).status()).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("다른 가게 판매자의 반품 승인을 실패한다. (FORBIDDEN, REFUND_NOT_FOUND)")
    void approveByOtherShopSellerFails() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id, 1000, 10);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        String seller2Token = testFixtures.login("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, seller1Id, shop1Id, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");

        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/approve", shop1Id, refundId)
                        .header("Authorization", "Bearer " + seller2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/approve", shop2Id, refundId)
                        .header("Authorization", "Bearer " + seller2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_FOUND"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        RefundRow r = testFixtures.refundRowOf(refundId);

        assertThat(r.status()).isEqualTo("REQUESTED");
        assertThat(s.line(product1Id).status()).isEqualTo("RETURN_REQUESTED");
        assertThat(pg.requests("/v1/payments/").stream().filter(req -> req.path().endsWith("/cancel"))).isEmpty();
    }

    @Test
    @DisplayName("같은 품목에 다른 키로 반품을 두 번 요청하면 두 번째를 실패한다. (INVALID_STATE_TRANSITION)")
    void requestReturnTwiceWithDifferentKeysFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.refundStatuses()).containsExactly("REQUESTED");
    }

    @Test
    @DisplayName("판매자 가게주문 조회 응답에 반품 요청의 환불 ID와 사유가 나온다.")
    void sellerShopOrderShowsReturnRefunds() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(orderId, product1Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/orders/{shopOrderId}", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds", hasSize(0)));

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));

        mockMvc.perform(get("/api/shops/{shopId}/orders/{shopOrderId}", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds", hasSize(1)))
                .andExpect(jsonPath("$.refunds[0].refundId").value(refundId.toString()))
                .andExpect(jsonPath("$.refunds[0].orderLineId").value(line1.toString()))
                .andExpect(jsonPath("$.refunds[0].reasonType").value("RETURN"))
                .andExpect(jsonPath("$.refunds[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.refunds[0].amount").value(1000))
                .andExpect(jsonPath("$.refunds[0].reason").value("상품 불량"));

        mockMvc.perform(get("/api/shops/{shopId}/orders", shopId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].refunds[0].refundId").value(refundId.toString()))
                .andExpect(jsonPath("$.items[0].refunds[0].status").value("REQUESTED"));
    }

    @Test
    @DisplayName("다른 주문의 품목 ID로 반품 요청을 실패한다. (ORDER_LINE_NOT_FOUND)")
    void requestReturnWithLineOfOtherOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);
        UUID order2Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(order1Id, product1Id);

        // When & Then
        ReturnRequest returnRequest = new ReturnRequest("상품 불량");
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", order2Id, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_LINE_NOT_FOUND"));

        OrderSnapshot s1 = testFixtures.snapshotOf(order1Id);
        OrderSnapshot s2 = testFixtures.snapshotOf(order2Id);

        assertThat(s1.refundStatuses()).isEmpty();
        assertThat(s2.refundStatuses()).isEmpty();
    }

    @Test
    @DisplayName("거절 이력이 있는 품목이라도 다른 주문의 경로로 반품을 요청하면 실패한다. (ORDER_LINE_NOT_FOUND)")
    void requestReturnOfRejectedLineWithOtherOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);
        UUID order2Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1 = testFixtures.orderLineIdOf(order1Id, product1Id);

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", order1Id, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));

        RejectReturnRequest rejectReturnRequest = new RejectReturnRequest("사용 흔적 있음");
        mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/reject", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReturnRequest)))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", order2Id, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_LINE_NOT_FOUND"));

        OrderSnapshot s1 = testFixtures.snapshotOf(order1Id);
        OrderSnapshot s2 = testFixtures.snapshotOf(order2Id);

        assertThat(s1.refundStatuses()).containsExactly("REJECTED");
        assertThat(s2.refundStatuses()).isEmpty();
    }
}
