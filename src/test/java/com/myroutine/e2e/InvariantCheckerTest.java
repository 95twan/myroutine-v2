package com.myroutine.e2e;

import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.support.Eventually;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.InvariantChecker;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InvariantCheckerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final JdbcTemplate jdbcTemplate;
    private final OutboxRelay outboxRelay;

    @Autowired
    public InvariantCheckerTest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                                JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.jdbcTemplate = jdbcTemplate;
        this.outboxRelay = outboxRelay;
    }

    @Test
    @DisplayName("주문 총액이 품목 금액 합과 다르면 orderAmounts가 그 주문 ID로 실패한다.")
    void orderAmountsDetectsWrongTotalAmount() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);

        assertThatCode(invariantChecker::orderAmounts).doesNotThrowAnyException();
        assertThatCode(invariantChecker::checkAll).doesNotThrowAnyException();

        // When
        jdbcTemplate.update("UPDATE orders.orders SET total_amount = 6001 WHERE id = ?", orderId);

        // Then
        assertThatThrownBy(invariantChecker::orderAmounts)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(orderId.toString());
        assertThatThrownBy(invariantChecker::checkAll).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("PAID 주문의 결제 금액이 총액과 다르면 orderAmounts가 그 주문 ID로 실패한다.")
    void orderAmountsDetectsWrongPaymentAmount() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);

        assertThatCode(invariantChecker::orderAmounts).doesNotThrowAnyException();
        assertThatCode(invariantChecker::checkAll).doesNotThrowAnyException();

        // When
        jdbcTemplate.update("UPDATE payment.payment SET amount = 6001 WHERE order_id = ?", orderId);

        // Then
        assertThatThrownBy(invariantChecker::orderAmounts)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(orderId.toString());
        assertThatThrownBy(invariantChecker::checkAll).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("승인된 결제의 주문이 PAID가 아니면 paymentsReflected가 그 주문 ID로 실패한다.")
    void paymentsReflectedDetectsUnreflectedPayment() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);

        assertThatCode(invariantChecker::paymentsReflected).doesNotThrowAnyException();
        assertThatCode(invariantChecker::checkAll).doesNotThrowAnyException();

        // When
        jdbcTemplate.update("UPDATE orders.orders SET status = 'PAYMENT_FAILED' WHERE id = ?", orderId);

        // Then
        assertThatThrownBy(invariantChecker::paymentsReflected)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(orderId.toString());
        assertThatThrownBy(invariantChecker::checkAll).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("입고 누계가 입고 이력 합과 다르면 stockBalance가 그 상품 ID로 실패한다.")
    void stockBalanceDetectsReceivedMismatch() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        assertThatCode(invariantChecker::stockBalance).doesNotThrowAnyException();
        assertThatCode(invariantChecker::checkAll).doesNotThrowAnyException();

        // When
        jdbcTemplate.update("UPDATE product.stock SET received = received + 5, available = available + 5 WHERE product_id = ?", productId);

        // Then
        assertThatThrownBy(invariantChecker::stockBalance)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(productId.toString());
        assertThatThrownBy(invariantChecker::checkAll).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("완료된 환불 합이 주문 총액을 넘으면 refundLimits가 그 주문 ID로 실패한다.")
    void refundLimitsDetectsOverRefund() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id, product2Id, product3Id);

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, testFixtures.orderLineIdOf(orderId, product1Id))
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThatCode(invariantChecker::refundLimits).doesNotThrowAnyException();
        assertThatCode(invariantChecker::checkAll).doesNotThrowAnyException();

        // When
        jdbcTemplate.update("UPDATE orders.refund SET amount = 6001 WHERE order_id = ?", orderId);

        // Then
        assertThatThrownBy(invariantChecker::refundLimits)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(orderId.toString());
        assertThatThrownBy(invariantChecker::checkAll).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("구매확정 품목의 정산 항목이 없으면 settlementOnce가 그 품목 ID로 실패한다.")
    void settlementOnceDetectsMissingItem() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, productId);
        UUID lineId = testFixtures.orderLineIdOf(orderId, productId);

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", orderId, lineId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10),
                () -> assertThat(testFixtures.countOf("settlement.settlement_item")).isEqualTo(1));

        assertThatCode(invariantChecker::settlementOnce).doesNotThrowAnyException();
        assertThatCode(invariantChecker::checkAll).doesNotThrowAnyException();

        // When
        jdbcTemplate.update("DELETE FROM settlement.settlement_item WHERE order_line_id = ?", lineId);

        // Then
        assertThatThrownBy(invariantChecker::settlementOnce)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(lineId.toString());
        assertThatThrownBy(invariantChecker::checkAll).isInstanceOf(AssertionError.class);
    }
}
