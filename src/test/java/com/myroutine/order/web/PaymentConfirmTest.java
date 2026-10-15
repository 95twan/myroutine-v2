package com.myroutine.order.web;

import com.myroutine.common.model.Ids;
import com.myroutine.order.application.CartService;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentConfirmTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final CartService cartService;

    @Autowired
    public PaymentConfirmTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, Clock clock, CartService cartService) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.cartService = cartService;
    }

    @Test
    @DisplayName("승인하면 200 PAID가 되고 재고가 확정되며 결제는 APPROVED, 주문한 수량만큼 장바구니에서 빠진다.")
    void confirmApproved() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount()
        );

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.reserved()).isEqualTo(0);
        assertThat(snapshot.sold()).isEqualTo(1);

        List<String> shopOrders = jdbcTemplate.queryForList(
                "SELECT status FROM orders.shop_order WHERE order_id = ?", String.class, orderId);
        assertThat(shopOrders).isNotEmpty().containsOnly("PAID");

        List<String> lines = jdbcTemplate.queryForList("""
                SELECT ol.status FROM orders.order_line ol
                JOIN orders.shop_order so ON so.id = ol.shop_order_id
                WHERE so.order_id = ?
                """, String.class, orderId);
        assertThat(lines).isNotEmpty().containsOnly("PAID");

        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT id, status FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("APPROVED");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(0);

        List<RecordedRequest> requests = pg.requests("/v1/payments/confirm");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).headers().get("idempotency-key")).isEqualTo(payment.get("id").toString());
    }

    @Test
    @DisplayName("카드사가 거절하면 402 PAYMENT_FAILED가 되고 재고가 원복되며 장바구니는 그대로다.")
    void confirmRejected() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount()
        );

        pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"));

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.details.pgCode").value("REJECT_CARD_COMPANY"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(100);
        assertThat(snapshot.reserved()).isEqualTo(0);
        assertThat(snapshot.sold()).isEqualTo(0);

        List<String> shopOrders = jdbcTemplate.queryForList(
                "SELECT status FROM orders.shop_order WHERE order_id = ?", String.class, orderId);
        assertThat(shopOrders).isNotEmpty().containsOnly("CANCELLED");

        List<String> lines = jdbcTemplate.queryForList("""
                SELECT ol.status FROM orders.order_line ol
                JOIN orders.shop_order so ON so.id = ol.shop_order_id
                WHERE so.order_id = ?
                """, String.class, orderId);
        assertThat(lines).isNotEmpty().containsOnly("CANCELLED");

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("PAYMENT_FAILED");
        assertThat(order.get("failure_reason")).isEqualTo("PG 거절: REJECT_CARD_COMPANY");

        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status, failure_code FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("FAILED");
        assertThat(payment.get("failure_code")).isEqualTo("REJECT_CARD_COMPANY");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 응답이 타임아웃되면 202가 되고 결제 UNKNOWN·주문 PAYMENT_IN_PROGRESS로 남으며 재고와 장바구니는 그대로다.")
    void confirmTimeout() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount()
        );

        pg.on("/v1/payments/confirm", Behavior.delay(1000));

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_IN_PROGRESS"));

        StockSnapshot snapshot = testFixtures.stockOf(productId);
        assertThat(snapshot.available()).isEqualTo(99);
        assertThat(snapshot.reserved()).isEqualTo(1);
        assertThat(snapshot.sold()).isEqualTo(0);

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("PAYMENT_IN_PROGRESS");

        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT status FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("UNKNOWN");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 주문의 승인을 실패한다. (ORDER_EXPIRED, PG 요청 0건)")
    void confirmExpiredOrder() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        testFixtures.setOrderExpired(orderId, clock.instant().minus(Duration.ofHours(1)));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount()
        );

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));

        List<RecordedRequest> requests = pg.requests("/v1/payments/confirm");
        assertThat(requests).hasSize(0);
    }

    @Test
    @DisplayName("같은 주문을 서로 다른 Idempotency-Key로 동시에 승인해도 PG 승인 요청은 1건이고 응답은 200 하나와 202 하나다.")
    void confirmConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount()
        );

        pg.on("/v1/payments/confirm", Behavior.delay(300));

        // When & Then
        Callable<Integer> task1 = () ->
                mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", Ids.newId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        Callable<Integer> task2 = () ->
                mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", Ids.newId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        List<ConcurrencyRunner.Result<Integer>> results = ConcurrencyRunner.run(List.of(task1, task2));
        List<Integer> statuses = results.stream().map(ConcurrencyRunner.Result::value).sorted().toList();
        assertThat(statuses).containsExactly(200, 202);

        List<RecordedRequest> requests = pg.requests("/v1/payments/confirm");
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("승인 금액이 주문 금액과 다른 요청을 실패한다. (AMOUNT_MISMATCH, PG 요청 0건, 주문은 PENDING_PAYMENT 그대로)")
    void confirmAmountMismatch() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                pendingOrder.pgOrderId(),
                pendingOrder.totalAmount() + 1
        );

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));


        List<RecordedRequest> requests = pg.requests("/v1/payments/confirm");
        assertThat(requests).hasSize(0);

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("이미 결제된 주문의 승인을 실패한다. (ORDER_ALREADY_PAID)")
    void confirmAlreadyPaid() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, productId);

        UUID orderId = pendingOrder.orderId();

        testFixtures.confirmPayment(buyerId, orderId);

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
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
    }

    @Test
    @DisplayName("결제 중 같은 상품을 더 담아 수량이 늘었어도(2→3) 승인되고 장바구니에는 1개가 남는다.")
    void confirmWhenCartQuantityIncreased() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        CheckoutResult pendingOrder = testFixtures.createPendingOrder(buyerId, Map.of(productId, 2));
        testFixtures.addToCart(buyerId, productId, 1);

        UUID orderId = pendingOrder.orderId();

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
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject("SELECT quantity FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 중 수량을 주문 수량 이하로 줄였어도(2→1) 오류 없이 PAID가 되고 항목은 삭제된다.")
    void confirmWhenCartQuantityDecreased() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);
        UUID addressId = testFixtures.address(buyerId);
        CheckoutResult checkout = testFixtures.checkout(buyerId, List.of(cartItemId), addressId);
        cartService.changeQuantity(buyerId, cartItemId, 1);

        UUID orderId = checkout.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                checkout.pgOrderId(),
                checkout.totalAmount()
        );

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("PAID");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("결제 중 장바구니 항목을 직접 삭제했어도 오류 없이 PAID가 된다.")
    void confirmWhenCartItemRemoved() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);
        UUID addressId = testFixtures.address(buyerId);
        CheckoutResult checkout = testFixtures.checkout(buyerId, List.of(cartItemId), addressId);
        cartService.removeItem(buyerId, cartItemId);

        UUID orderId = checkout.orderId();

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                "pk_" + orderId,
                checkout.pgOrderId(),
                checkout.totalAmount()
        );

        // When & Then
        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status FROM orders.orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("PAID");
    }

}
