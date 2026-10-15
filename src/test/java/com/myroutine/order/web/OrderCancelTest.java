package com.myroutine.order.web;

import com.myroutine.common.model.Ids;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ReleaseReason;
import com.myroutine.product.api.ReserveItem;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderCancelTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProductApi productApi;

    @Autowired
    public OrderCancelTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, ProductApi productApi) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.productApi = productApi;
    }

    @Test
    @DisplayName("결제 전 주문을 취소하면 200 CANCELLED가 되고 재고 예약이 풀리며 장바구니와 PG는 건드리지 않는다.")
    void cancel() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(100);
        assertThat(snapshot.reserved()).isEqualTo(0);

        Map<String, Object> stockReservation = jdbcTemplate.queryForMap(
                "SELECT status FROM product.stock_reservation WHERE order_id = ? AND product_id = ?", orderId, productId);
        assertThat(stockReservation.get("status")).isEqualTo("RELEASED");

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("CANCELLED");
        assertThat(order.get("failure_reason")).isNull();

        List<String> shopOrders = jdbcTemplate.queryForList(
                "SELECT status FROM orders.shop_order WHERE order_id = ?", String.class, orderId);
        assertThat(shopOrders).isNotEmpty().containsOnly("CANCELLED");

        List<String> lines = jdbcTemplate.queryForList("""
                SELECT ol.status FROM orders.order_line ol
                JOIN orders.shop_order so ON so.id = ol.shop_order_id
                WHERE so.order_id = ?
                """, String.class, orderId);
        assertThat(lines).isNotEmpty().containsOnly("CANCELLED");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(1);

        List<RecordedRequest> requests = pg.requests("/v1/payments/confirm");
        assertThat(requests).hasSize(0);
    }

    @Test
    @DisplayName("취소한 뒤 같은 장바구니 항목으로 다시 체크아웃하면 201이다. (재고가 풀렸다)")
    void checkoutAfterCancel() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 2);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);
        UUID addressId = testFixtures.address(buyerId);
        CheckoutResult checkout = testFixtures.checkout(buyerId, List.of(cartItemId), addressId);

        UUID orderId = checkout.orderId();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())            // 체크아웃은 @Idempotent
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(List.of(cartItemId), addressId))))
                .andExpect(status().isCreated());

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(0);
        assertThat(snapshot.reserved()).isEqualTo(2);
    }

    @Test
    @DisplayName("남의 주문 취소를 실패한다. (ORDER_NOT_FOUND, 주문·재고 그대로)")
    void cancelOthersOrder() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        String token = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(99);
        assertThat(snapshot.reserved()).isEqualTo(1);

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("PAYMENT_IN_PROGRESS 주문 취소를 실패한다. (INVALID_STATE_TRANSITION, 재고는 reserved 그대로)")
    void cancelPaymentInProgress() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        testFixtures.confirmPayment(buyerId, orderId);

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(99);
        assertThat(snapshot.reserved()).isEqualTo(1);
    }

    @Test
    @DisplayName("PAID 주문 취소를 실패한다. (INVALID_STATE_TRANSITION, 재고 sold 그대로)")
    void cancelPaid() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        testFixtures.confirmPayment(buyerId, orderId);

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(99);
        assertThat(snapshot.reserved()).isEqualTo(0);
        assertThat(snapshot.sold()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 취소·만료된 주문 취소를 실패한다. (INVALID_STATE_TRANSITION, 재고가 두 번 풀리지 않는다)")
    void cancelAlreadyClosedOrder() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(100);
        assertThat(snapshot.reserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 주문을 동시에 취소해도 200 하나와 409 하나이고 재고는 1회분만 원복된다.")
    void cancelConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        // When & Then
        Callable<Integer> task1 = () -> mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();


        Callable<Integer> task2 = () -> mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();

        List<ConcurrencyRunner.Result<Integer>> results = ConcurrencyRunner.run(List.of(task1, task2));
        List<Integer> statuses = results.stream()
                .map(ConcurrencyRunner.Result::value)
                .toList();

        assertThat(statuses).isNotEmpty().containsExactlyInAnyOrder(200, 409);

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(100);
        assertThat(snapshot.reserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("취소된 주문의 승인을 실패한다. (INVALID_STATE_TRANSITION, PG 요청 0건)")
    void confirmAfterCancel() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount()
        );

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        List<RecordedRequest> requests = pg.requests("/v1/payments/confirm");
        assertThat(requests).hasSize(0);
    }

    @RepeatedTest(20)
    @DisplayName("같은 두 상품이 든 주문의 취소와 상품 순서를 뒤집은 주문의 체크아웃이 동시에 실행돼도 데드락 없이 끝난다. (20회 반복)")
    void cancelAndCheckoutCrossLock() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID orderA = Ids.newId();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        productApi.reserve(orderA, List.of(new ReserveItem(productId1, 1), new ReserveItem(productId2, 1)), expiresAt);

        // When & Then
        Callable<Boolean> cancel = () -> {
            productApi.releaseReservation(orderA, ReleaseReason.PAYMENT_FAILED);
            return true;
        };
        Callable<Boolean> checkout = () -> {
            productApi.reserve(Ids.newId(), List.of(new ReserveItem(productId2, 1), new ReserveItem(productId1, 1)), expiresAt);
            return true;
        };

        List<ConcurrencyRunner.Result<Boolean>> results = ConcurrencyRunner.run(List.of(cancel, checkout));
        assertThat(results).allMatch(ConcurrencyRunner.Result::ok);
    }

}
