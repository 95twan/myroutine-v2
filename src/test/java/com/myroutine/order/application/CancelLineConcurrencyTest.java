package com.myroutine.order.application;

import com.myroutine.common.model.Ids;
import com.myroutine.order.web.CancelLineRequest;
import com.myroutine.order.web.ShipRequest;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OrderSnapshot;
import com.myroutine.support.TestFixtures;
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
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class CancelLineConcurrencyTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    public CancelLineConcurrencyTest(TestFixtures testFixtures, MockMvc mockMvc, ObjectMapper objectMapper) {
        this.testFixtures = testFixtures;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("같은 품목을 다른 Idempotency-Key로 동시에 2번 취소하면 환불은 1건 COMPLETED이고 나머지는 409다.")
    void cancelSameLineConcurrently() throws Exception {
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

        // When
        Callable<MvcResult> task1 = () -> mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Callable<MvcResult> task2 = () -> mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(List.of(task1, task2));

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 409) {
                String code = objectMapper.readTree(response.getResponse().getContentAsString()).get("code").asString();
                assertThat(code).isIn("DUPLICATE_RESOURCE", "INVALID_STATE_TRANSITION");
            }
        }

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);
        assertThat(snapshot.refundStatuses()).hasSize(1);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("COMPLETED");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(1000);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(1000);
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(1);
        assertThat(testFixtures.stockOf(product1Id).available()).isEqualTo(10);
        assertThat(testFixtures.stockOf(product1Id).sold()).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 주문의 서로 다른 품목 2개를 동시에 취소하면 둘 다 COMPLETED이고 payment cancelled_amount는 두 품목 합이다.")
    void cancelDifferentLinesOfSameOrderConcurrently() throws InterruptedException {
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

        // When
        Callable<MvcResult> task1 = () -> mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, line1Id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Callable<MvcResult> task2 = () -> mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, line2Id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(List.of(task1, task2));

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsExactly(200, 200);

        OrderSnapshot snapshot = testFixtures.snapshotOf(orderId);
        assertThat(snapshot.refundStatuses()).hasSize(2);
        assertThat(snapshot.refundStatuses().get(0)).isEqualTo("COMPLETED");
        assertThat(snapshot.refundStatuses().get(1)).isEqualTo("COMPLETED");
        assertThat(snapshot.line(product1Id).refundedAmount()).isEqualTo(1000);
        assertThat(snapshot.line(product2Id).refundedAmount()).isEqualTo(2000);
        assertThat(snapshot.line(product3Id).refundedAmount()).isEqualTo(0);
        assertThat(snapshot.payment().cancelledAmount()).isEqualTo(3000);
        assertThat(snapshot.payment().status()).isEqualTo("PARTIAL_CANCELLED");
        assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).hasSize(2);
    }

    @Test
    @DisplayName("같은 품목의 취소와 같은 가게주문의 발송을 동시에 보내도 발송된 품목의 환불은 남지 않는다.")
    void cancelAndShipConcurrently() throws InterruptedException {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        String sellerToken = testFixtures.login("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);
        UUID lineId = testFixtures.orderLineIdOf(orderId, product1Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        ShipRequest shipRequest = new ShipRequest("대한통운", "1234567890");

        // When
        Callable<MvcResult> task1 = () -> mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Callable<MvcResult> task2 = () -> mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipRequest)))
                .andReturn();

        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(List.of(task1, task2));

        // Then
        int cancelStatus = results.get(0).value().getResponse().getStatus();
        int shipStatus = results.get(1).value().getResponse().getStatus();
        OrderSnapshot s = testFixtures.snapshotOf(orderId);

        // 1) 가능한 조합은 셋뿐이다
        assertThat(List.of(cancelStatus, shipStatus))
                .isIn(List.of(200, 409), List.of(409, 200), List.of(200, 200));

        // 2) 어느 경우에도 없어야 하는 상태: 발송된 품목에 환불이 남음
        if (s.line(product1Id).status().equals("SHIPPED")) {
            assertThat(s.refundStatuses()).isEmpty();
        }

        // 3) 조합별 최종 상태
        if (cancelStatus == 200 && shipStatus == 409) {          // 취소가 먼저 환불을 만들었다
            // 발송 응답 $.code = REFUND_IN_PROGRESS
            assertThat(s.refundStatuses()).containsExactly("COMPLETED");
            assertThat(s.line(product1Id).status()).isEqualTo("CANCELLED");
            assertThat(s.line(product2Id).status()).isEqualTo("PAID");
            assertThat(s.line(product3Id).status()).isEqualTo("PAID");
            assertThat(s.shopOrderStatus()).isEqualTo("PAID");
        } else if (cancelStatus == 409 && shipStatus == 200) {   // 발송이 먼저였다
            // 취소 응답 $.code = INVALID_STATE_TRANSITION
            assertThat(s.refundStatuses()).isEmpty();
            assertThat(s.line(product1Id).status()).isEqualTo("SHIPPED");
            assertThat(s.line(product1Id).refundedAmount()).isEqualTo(0L);
            assertThat(s.shopOrderStatus()).isEqualTo("SHIPPED");
            assertThat(pg.requests("/v1/payments/").stream().filter(r -> r.path().endsWith("/cancel"))).isEmpty();
        } else {                                                  // 취소가 끝난 뒤 발송 성공
            assertThat(s.refundStatuses()).containsExactly("COMPLETED");
            assertThat(s.line(product1Id).status()).isEqualTo("CANCELLED");
            assertThat(s.line(product2Id).status()).isEqualTo("SHIPPED");
            assertThat(s.line(product3Id).status()).isEqualTo("SHIPPED");
            assertThat(s.shopOrderStatus()).isEqualTo("SHIPPED");
        }
    }
}
