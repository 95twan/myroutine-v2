package com.myroutine.order.application;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.order.web.RejectReturnRequest;
import com.myroutine.order.web.ReturnRequest;
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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReturnConcurrencyTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReturnConcurrencyTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("같은 반품 요청을 승인과 거절로 동시에 처리하면 한쪽만 성공하고 환불·품목·결제가 그 결과와 일치한다.")
    void approveAndRejectSameReturnConcurrently() throws Exception {
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

        ReturnRequest returnRequest = new ReturnRequest("상품 불량");
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/return-requests", orderId, line1)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.refundId"));

        // When
        Callable<MvcResult> task1 = () -> mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/approve", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andReturn();

        RejectReturnRequest rejectReturnRequest = new RejectReturnRequest("사용 흔적");
        Callable<MvcResult> task2 = () -> mockMvc.perform(post("/api/shops/{shopId}/refunds/{refundId}/reject", shopId, refundId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReturnRequest)))
                .andReturn();

        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(List.of(task1, task2));

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 409) {
                String code = objectMapper.readTree(response.getResponse().getContentAsString()).get("code").asString();
                assertThat(code).isIn("INVALID_STATE_TRANSITION", "CONFLICT_RETRY");
            } else {
                String status = JsonPath.read(response.getResponse().getContentAsString(), "$.status");
                RefundRow r = testFixtures.refundRowOf(refundId);
                OrderSnapshot s = testFixtures.snapshotOf(orderId);
                if (status.equals("COMPLETED")) {
                    assertThat(r.status()).isEqualTo("COMPLETED");
                    assertThat(r.rejectReason()).isNull();
                    assertThat(s.refundStatuses()).containsExactly("COMPLETED");
                    assertThat(s.line(product1Id).status()).isEqualTo("RETURNED");
                    assertThat(s.line(product1Id).refundedAmount()).isEqualTo(1000);
                    assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(10);
                    assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(0);
                    assertThat(pg.requests("/v1/payments/").stream().filter(req -> req.path().endsWith("/cancel"))).hasSize(1);
                } else {
                    assertThat(r.status()).isEqualTo("REJECTED");
                    assertThat(r.rejectReason()).isEqualTo("사용 흔적");
                    assertThat(s.refundStatuses()).containsExactly("REJECTED");
                    assertThat(s.line(product1Id).status()).isEqualTo("DELIVERED");
                    assertThat(s.line(product1Id).refundedAmount()).isEqualTo(0);
                    assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(9);
                    assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(1);
                    assertThat(pg.requests("/v1/payments/").stream().filter(req -> req.path().endsWith("/cancel"))).isEmpty();
                }
            }
        }

    }
}
