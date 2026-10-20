package com.myroutine.order.web;

import com.myroutine.order.application.CheckoutResult;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SellerOrderControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    public SellerOrderControllerTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, EntityManagerFactory entityManagerFactory) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Test
    @DisplayName("가게 A·B 상품을 한 주문으로 결제하면 A 판매자의 목록에는 A 가게주문만 나온다.")
    void getShopOrdersReturnsOnlyOwnShopOrders() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shop1Id);

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/orders", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].shopOrderId").value(shopOrderId.toString()));
    }

    @Test
    @DisplayName("상태로 필터해 가게주문 목록을 조회한다.")
    void getShopOrdersFilteredByStatus() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID order1Id = testFixtures.createPaidOrder(buyerId, product1Id);
        testFixtures.createPendingOrder(buyerId, product1Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(order1Id, shop1Id);

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/orders", shop1Id)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].shopOrderId").value(shopOrderId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PAID"));
    }

    @Test
    @DisplayName("발송한 뒤 배송완료하면 가게주문과 품목 상태가 차례로 바뀐다.")
    void shipThenDeliver() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID order1Id = testFixtures.createPaidOrder(buyerId, product1Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(order1Id, shop1Id);

        ShipRequest request = new ShipRequest(
                "CJ 대한통운",
                "1234567890"
        );

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shop1Id, shopOrderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.carrier").value("CJ 대한통운"))
                .andExpect(jsonPath("$.trackingNumber").value("1234567890"))
                .andExpect(jsonPath("$.shippedAt").value(notNullValue()))
                .andExpect(jsonPath("$.deliveredAt").value(nullValue()))
                .andExpect(jsonPath("$.lines[*].status").value("SHIPPED"));

        mockMvc.perform(get("/api/shops/{shopId}/orders/{shopOrderId}", shop1Id, shopOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.carrier").value("CJ 대한통운"))
                .andExpect(jsonPath("$.trackingNumber").value("1234567890"))
                .andExpect(jsonPath("$.shippedAt").value(notNullValue()))
                .andExpect(jsonPath("$.deliveredAt").value(nullValue()))
                .andExpect(jsonPath("$.lines[*].status").value("SHIPPED"));

        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/deliver", shop1Id, shopOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").value(notNullValue()))
                .andExpect(jsonPath("$.lines[*].status").value("DELIVERED"));

        mockMvc.perform(get("/api/shops/{shopId}/orders/{shopOrderId}", shop1Id, shopOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").value(notNullValue()))
                .andExpect(jsonPath("$.lines[*].status").value("DELIVERED"));
    }

    @Test
    @DisplayName("다른 가게 판매자가 내 가게 shopId로 호출하면 실패한다. (403 FORBIDDEN)")
    void getShopOrderFailsWhenNotShopOwner() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shop2Id);

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/orders/{shopOrderId}", shop2Id, shopOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("자기 가게 shopId로 남의 가게주문 ID를 조회하면 실패한다. (404 SHOP_ORDER_NOT_FOUND)")
    void getShopOrderFailsWhenShopOrderBelongsToOtherShop() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shop2Id);

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/orders/{shopOrderId}", shop1Id, shopOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("결제 대기(PENDING) 가게주문의 발송을 실패한다. (409 INVALID_STATE_TRANSITION)")
    void shipFailsWhenShopOrderIsPending() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);

        UUID shopOrderId = testFixtures.shopOrderIdOf(order.orderId(), shop1Id);

        ShipRequest request = new ShipRequest(
                "CJ 대한통운",
                "1234567890"
        );

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/orders/{shopOrderId}/ship", shop1Id, shopOrderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("목록 조회의 SQL 수는 가게주문이 3개일 때와 10개일 때 같다.")
    void getShopOrdersQueryCountDoesNotGrowWithRows() throws Exception {
        // Given
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        for (int i = 0; i < 3; i++) {
            testFixtures.createPaidOrder(buyerId, product1Id);
        }

        statistics.clear();

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/orders", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long first = statistics.getPrepareStatementCount();

        for (int i = 0; i < 7; i++) {
            testFixtures.createPaidOrder(buyerId, product1Id);
        }

        statistics.clear();

        mockMvc.perform(get("/api/shops/{shopId}/orders", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long second = statistics.getPrepareStatementCount();

        assertThat(second).isEqualTo(first);
    }
}
