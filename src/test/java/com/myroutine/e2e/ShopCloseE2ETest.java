package com.myroutine.e2e;

import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.product.api.event.ProductTopics;
import com.myroutine.shop.api.event.ShopTopics;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ShopCloseE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final OutboxRelay outboxRelay;

    @Autowired
    public ShopCloseE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                            OutboxAssert outboxAssert, OutboxRelay outboxRelay) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.outboxRelay = outboxRelay;
    }

    @Test
    @DisplayName("진행 중 주문이 있으면 폐업이 거부되고, 주문이 끝난 뒤 폐업하면 상품이 단종되고 SELLER 권한이 회수된다.")
    void closeShopRefusedThenClosedAfterOrdersFinish() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String sellerToken = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 10000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10),
                () -> assertThat(testFixtures.memberSnapshotOf(sellerId).role()).isEqualTo("SELLER"));

        // 1. 진행 중 주문
        mockMvc.perform(delete("/api/shops/{shopId}", shopId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_HAS_ACTIVE_ORDERS"));

        ShopSnapshot refused = testFixtures.shopSnapshotOf(shopId);
        assertThat(refused.status()).isEqualTo("ACTIVE");
        assertThat(refused.closedAt()).isNull();
        assertThat(outboxAssert.rows(ShopTopics.SHOP_CLOSED)).isEmpty();
        assertThat(testFixtures.memberSnapshotOf(sellerId).role()).isEqualTo("SELLER");
        assertThat(testFixtures.productSnapshotOf(productId).status()).isEqualTo("ON_SALE");

        // 2. 주문 종결
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"1234567890\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/deliver", shopId, shopOrderId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", orderId, testFixtures.orderLineIdOf(orderId, productId))
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());
        assertThat(testFixtures.snapshotOf(orderId).shopOrderStatus()).isEqualTo("COMPLETED");

        // 3. 폐업
        mockMvc.perform(delete("/api/shops/{shopId}", shopId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isNoContent());

        ShopSnapshot closed = testFixtures.shopSnapshotOf(shopId);
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
        assertThat(outboxAssert.rows(ShopTopics.SHOP_CLOSED)).hasSize(1);

        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> {
            assertThat(testFixtures.productSnapshotOf(productId).status()).isEqualTo("DISCONTINUED");
            assertThat(testFixtures.memberSnapshotOf(sellerId).role()).isEqualTo("USER");
            assertThat(testFixtures.unsettledItemCount()).isEqualTo(1);
        });
        List<OutboxSnapshot> discontinued = outboxAssert.rows(ProductTopics.PRODUCT_DISCONTINUED);
        assertThat(discontinued).hasSize(1);
        assertThat(discontinued.get(0).payload().get("reason").asText()).isEqualTo("SHOP_CLOSED");

        // 4. 불변식
        invariantChecker.checkAll();
    }
}
