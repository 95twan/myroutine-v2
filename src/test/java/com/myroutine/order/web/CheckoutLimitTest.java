package com.myroutine.order.web;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.order.application.OrderExpirationService;
import com.myroutine.order.application.PendingOrderCancelService;
import com.myroutine.support.Behavior;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CheckoutLimitTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final PendingOrderCancelService pendingOrderCancelService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OrderExpirationService orderExpirationService;

    @Autowired
    public CheckoutLimitTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            PendingOrderCancelService pendingOrderCancelService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            OrderExpirationService orderExpirationService
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.pendingOrderCancelService = pendingOrderCancelService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.orderExpirationService = orderExpirationService;
    }

    @Test
    @DisplayName("결제 대기 주문이 3건이면 체크아웃을 실패한다. (409 PENDING_ORDER_LIMIT_EXCEEDED)")
    void checkoutFailsPendingOrderLimit() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID order1Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        UUID order2Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        UUID order3Id = testFixtures.createPendingOrder(buyerId, productId).orderId();

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_ORDER_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.details.orderIds").value(containsInAnyOrder(order1Id.toString(), order2Id.toString(), order3Id.toString())));

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.orders WHERE member_id = ?", Integer.class, buyerId);
        assertThat(orderCount).isEqualTo(3);
    }

    @Test
    @DisplayName("결제 중인 주문도 결제 대기로 센다.")
    void checkoutCountsPaymentInProgressAsPending() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        UUID order1Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        testFixtures.confirmPayment(buyerId, order1Id);
        pg.reset();

        testFixtures.createPendingOrder(buyerId, productId);
        testFixtures.createPendingOrder(buyerId, productId);

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_ORDER_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("결제 대기 주문이 2건이면 체크아웃한다.")
    void checkoutWithTwoPendingOrders() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        testFixtures.createPendingOrder(buyerId, productId);
        testFixtures.createPendingOrder(buyerId, productId);

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.orders WHERE member_id = ?", Integer.class, buyerId);
        assertThat(orderCount).isEqualTo(3);
    }

    @Test
    @DisplayName("최근 24시간에 만든 주문 중 결제되지 않은 주문이 5건이면 체크아웃을 실패한다. (429 UNPAID_ORDER_LIMIT_EXCEEDED)")
    void checkoutFailsUnpaidOrderLimit() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        UUID order1Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order1Id);

        UUID order2Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        testFixtures.setOrderExpired(order2Id, Instant.now().minusSeconds(1));
        orderExpirationService.expireBatch();

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        UUID order3Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        testFixtures.confirmPayment(buyerId, order3Id);

        pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"));
        UUID order4Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        try {
            testFixtures.confirmPayment(buyerId, order4Id);
        } catch (BusinessException ignored) {

        }

        testFixtures.createPendingOrder(buyerId, productId);

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("UNPAID_ORDER_LIMIT_EXCEEDED"));

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.orders WHERE member_id = ?", Integer.class, buyerId);
        assertThat(orderCount).isEqualTo(5);
    }

    @Test
    @DisplayName("결제하지 않은 주문 제한의 다시 시도할 시간은 다섯 번째로 최근 주문이 24시간 창에서 빠질 때까지다.")
    void unpaidOrderLimitRetryAfter() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        UUID order1Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order1Id);

        UUID order2Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order2Id);

        UUID order3Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order3Id);

        UUID order4Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order4Id);

        UUID order5Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order5Id);

        testFixtures.setOrderCreatedAt(order1Id, Instant.now().minus(Duration.ofHours(23)));

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(allOf(greaterThanOrEqualTo(3590), lessThanOrEqualTo(3600))));
    }

    @Test
    @DisplayName("24시간이 지난 결제되지 않은 주문은 세지 않는다.")
    void checkoutIgnoresUnpaidOrdersOutsideWindow() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        UUID order1Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order1Id);

        UUID order2Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order2Id);

        UUID order3Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order3Id);

        UUID order4Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order4Id);

        UUID order5Id = testFixtures.createPendingOrder(buyerId, productId).orderId();
        pendingOrderCancelService.cancel(buyerId, order5Id);

        testFixtures.setOrderCreatedAt(order1Id, Instant.now().minus(Duration.ofHours(24)).minusSeconds(60));

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("결제한 주문은 결제하지 않은 주문으로 세지 않는다.")
    void checkoutIgnoresPaidOrders() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        testFixtures.createPaidOrder(buyerId, productId);
        testFixtures.createPaidOrder(buyerId, productId);
        testFixtures.createPaidOrder(buyerId, productId);
        testFixtures.createPaidOrder(buyerId, productId);
        testFixtures.createPaidOrder(buyerId, productId);

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
