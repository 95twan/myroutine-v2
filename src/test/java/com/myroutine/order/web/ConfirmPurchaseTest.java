package com.myroutine.order.web;

import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OrderSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConfirmPurchaseTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;

    @Autowired
    public ConfirmPurchaseTest(MockMvc mockMvc, TestFixtures testFixtures) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("배송완료된 품목을 구매확정하면 CONFIRMED가 되고 가게주문이 COMPLETED가 된다.")
    void confirmPurchase() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1Id = testFixtures.orderLineIdOf(order1Id, product1Id);

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", order1Id, line1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderLineId").value(line1Id.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value(notNullValue()));

        OrderSnapshot s = testFixtures.snapshotOf(order1Id);
        assertThat(s.line(product1Id).status()).isEqualTo("CONFIRMED");
        assertThat(s.line(product1Id).confirmedAt()).isNotNull();
        assertThat(s.shopOrderStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("이미 확정된 품목의 구매확정을 실패한다. (재요청)")
    void confirmAlreadyConfirmedLineFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1Id = testFixtures.orderLineIdOf(order1Id, product1Id);

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", order1Id, line1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Instant confirmedAt = testFixtures.snapshotOf(order1Id).line(product1Id).confirmedAt();

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", order1Id, line1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        assertThat(confirmedAt).isEqualTo(testFixtures.snapshotOf(order1Id).line(product1Id).confirmedAt());
    }

    @Test
    @DisplayName("남의 주문의 구매확정을 실패한다. (404)")
    void confirmOthersOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID line1Id = testFixtures.orderLineIdOf(order1Id, product1Id);

        String otherToken = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", order1Id, line1Id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(testFixtures.snapshotOf(order1Id).line(product1Id).status()).isEqualTo("DELIVERED");
        OrderSnapshot s1 = testFixtures.snapshotOf(order1Id);
        assertThat(s1.line(product1Id).confirmedAt()).isNull();
    }
}
