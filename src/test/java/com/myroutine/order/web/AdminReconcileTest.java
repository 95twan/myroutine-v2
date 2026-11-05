package com.myroutine.order.web;

import com.myroutine.order.application.CheckoutResult;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminReconcileTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminReconcileTest(MockMvc mockMvc, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("PG에 승인 기록이 있는 UNKNOWN 결제를 대사하면 주문이 PAID가 된다.")
    void reconcileUnknownPaymentWithApprovalToPaid(CapturedOutput output) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        UUID orderId = testFixtures.confirmPayment(buyerId, order.orderId()).orderId();
        testFixtures.setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60));

        // When & Then
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.status()).isEqualTo("PAID");
        assertThat(pg.requests("/v1/payments/orders/")).hasSize(1);
        assertThat(output.getAll()).contains("target=" + orderId + " by=" + adminId);
    }

    @Test
    @DisplayName("PG에 승인 기록이 없는 UNKNOWN 결제를 대사하면 주문이 PAYMENT_FAILED가 된다.")
    void reconcileUnknownPaymentWithoutApprovalToPaymentFailed() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.drop());
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        UUID orderId = testFixtures.confirmPayment(buyerId, order.orderId()).orderId();
        testFixtures.setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60));

        // When & Then
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.status()).isEqualTo("PAYMENT_FAILED");
        assertThat(testFixtures.cartSnapshotOf(buyerId).quantity(product1Id)).isEqualTo(1);
        assertThat(pg.requests("/v1/payments/orders/")).hasSize(1);
    }

    @Test
    @DisplayName("PG 조회도 실패하면 주문 상태를 그대로 둔다.")
    void reconcileKeepsStatusWhenQueryFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        pg.on("/v1/payments/orders", Behavior.serverError());
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        UUID orderId = testFixtures.confirmPayment(buyerId, order.orderId()).orderId();
        testFixtures.setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60));

        // When & Then
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_IN_PROGRESS"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.payment().reconcileAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 끝난 주문을 대사하면 결제 상태와 상관없이 현재 주문 상태를 돌려준다.")
    void reconcileFinishedOrderReturnsCurrentStatus() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        UUID orderId = testFixtures.confirmPayment(buyerId, order.orderId()).orderId();
        testFixtures.setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60));
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        jdbcTemplate.update("UPDATE payment.payment SET status = 'CANCELLED' WHERE order_id = ?", orderId);

        // When & Then
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("결과가 확정되지 않은 결제(UNKNOWN) 목록을 조회한다.")
    void listUnknownPayments() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        UUID orderId = testFixtures.confirmPayment(buyerId, order.orderId()).orderId();
        UUID paymentId = testFixtures.snapshotOf(orderId).payment().id();

        // When & Then
        mockMvc.perform(get("/admin/api/payments")
                        .param("status", "UNKNOWN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].pgOrderId").value(order.pgOrderId()))
                .andExpect(jsonPath("$.items[0].amount").value(1000))
                .andExpect(jsonPath("$.items[0].createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.items[0].updatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.items[0].paymentKey").doesNotExist());
    }

    @Test
    @DisplayName("결제가 없는 주문을 대사하면 현재 주문 상태를 돌려준다.")
    void reconcileOrderWithoutPaymentReturnsCurrentStatus() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);

        // When & Then
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", order.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(pg.requests("/v1/payments/")).isEmpty();
    }

    @Test
    @DisplayName("30초 안에 바뀐 주문을 대사하면 PG를 조회하지 않고 현재 주문 상태를 돌려준다.")
    void reconcileRecentOrderReturnsCurrentStatus() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id);
        UUID orderId = testFixtures.confirmPayment(buyerId, order.orderId()).orderId();

        // When & Then
        mockMvc.perform(post("/admin/api/payments/{orderId}/reconcile", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_IN_PROGRESS"));

        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.payment().reconcileAttempts()).isEqualTo(0);
        assertThat(pg.requests("/v1/payments/orders/")).isEmpty();
    }
}
