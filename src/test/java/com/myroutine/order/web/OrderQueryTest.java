package com.myroutine.order.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderQueryTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    public OrderQueryTest(MockMvc mockMvc, TestFixtures testFixtures, EntityManagerFactory entityManagerFactory) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Test
    @DisplayName("내 주문 목록을 커서 페이징으로 조회한다.")
    void getMyOrdersWithCursor() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);
        List<UUID> orderIds = List.of(
                testFixtures.checkout(buyerId, List.of(cartItemId), addressId).orderId(),
                testFixtures.checkout(buyerId, List.of(cartItemId), addressId).orderId(),
                testFixtures.checkout(buyerId, List.of(cartItemId), addressId).orderId(),
                testFixtures.checkout(buyerId, List.of(cartItemId), addressId).orderId(),
                testFixtures.checkout(buyerId, List.of(cartItemId), addressId).orderId()
        );

        UUID otherId = testFixtures.signup("other@test.com");
        UUID otherAddressId = testFixtures.address(otherId);
        UUID otherCartItemId = testFixtures.addToCart(otherId, productId, 2);
        testFixtures.checkout(otherId, List.of(otherCartItemId), otherAddressId);

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[*].orderId");
        String nextCursor = JsonPath.read(body, "$.nextCursor");

        testFixtures.checkout(buyerId, List.of(cartItemId), addressId);

        while (nextCursor != null) {
            result = mockMvc.perform(get("/api/orders")
                            .header("Authorization", "Bearer " + token)
                            .queryParam("cursor", nextCursor)
                            .queryParam("size", "2"))
                    .andExpect(status().isOk())
                    .andReturn();

            body = result.getResponse().getContentAsString();
            ids.addAll(JsonPath.read(body, "$.items[*].orderId"));
            nextCursor = JsonPath.read(body, "$.nextCursor");
        }

        assertThat(ids).containsExactlyElementsOf(orderIds.reversed().stream().map(UUID::toString).toList());
    }

    @Test
    @DisplayName("주문 상세를 조회하면 가게주문과 품목이 함께 내려온다.")
    void getMyOrderDetail() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID shopId2 = testFixtures.openShop(memberId, "2222222222");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId1, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId1, 2000, 100);
        UUID productId3 = testFixtures.registerProduct(memberId, shopId2, 3000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 3);
        UUID cartItemId3 = testFixtures.addToCart(buyerId, productId3, 4);
        CheckoutResult checkout = testFixtures.checkout(buyerId, List.of(cartItemId1, cartItemId2, cartItemId3), addressId);

        // When & Then
        mockMvc.perform(get("/api/orders/{id}", checkout.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(checkout.orderId().toString()))
                .andExpect(jsonPath("$.status").value(OrderStatus.PENDING_PAYMENT.name()))
                .andExpect(jsonPath("$.totalAmount").value(1000 * 2 + 2000 * 3 + 3000 * 4))
                .andExpect(jsonPath("$.shopOrders", hasSize(2)))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')]", shopId1).exists())
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[*]", shopId1).value(hasSize(2)))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[*].productId", shopId1).value(containsInAnyOrder(productId1.toString(), productId2.toString())))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')]", shopId2).exists())
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[*]", shopId2).value(hasSize(1)))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[*].productId", shopId2).value(contains(productId3.toString())))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[?(@.productId == '%s')].unitPrice", shopId2, productId3).value(3000))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[?(@.productId == '%s')].quantity", shopId2, productId3).value(4))
                .andExpect(jsonPath("$.shopOrders[?(@.shopId == '%s')].lines[?(@.productId == '%s')].lineAmount", shopId2, productId3).value(3000 * 4));
    }

    @Test
    @DisplayName("주문 상세 조회를 실패한다. (다른 회원의 주문, 404 ORDER_NOT_FOUND)")
    void getOthersOrderFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 4);
        CheckoutResult checkout = testFixtures.checkout(buyerId, List.of(cartItemId), addressId);

        String token = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(get("/api/orders/{id}", checkout.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("주문 상세 조회의 SQL 수는 가게주문이 1개일 때와 3개일 때 같다.")
    void getMyOrderDetailQueryCountIsConstant() throws Exception {
        // Given
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID shopId2 = testFixtures.openShop(memberId, "2222222222");
        UUID shopId3 = testFixtures.openShop(memberId, "3333333333");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId1, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId2, 2000, 100);
        UUID productId3 = testFixtures.registerProduct(memberId, shopId3, 3000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 3);
        UUID cartItemId3 = testFixtures.addToCart(buyerId, productId3, 4);
        CheckoutResult checkout1 = testFixtures.checkout(buyerId, List.of(cartItemId1), addressId);

        // When & Then
        // 웜 업 - token version 채우기
        mockMvc.perform(get("/api/orders/{id}", checkout1.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        statistics.clear();

        mockMvc.perform(get("/api/orders/{id}", checkout1.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long first = statistics.getPrepareStatementCount();

        CheckoutResult checkout2 = testFixtures.checkout(buyerId, List.of(cartItemId1, cartItemId2, cartItemId3), addressId);

        statistics.clear();

        mockMvc.perform(get("/api/orders/{id}", checkout2.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long second = statistics.getPrepareStatementCount();

        assertThat(second).isEqualTo(first);
    }
}
