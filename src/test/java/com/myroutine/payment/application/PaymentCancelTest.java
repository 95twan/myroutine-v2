package com.myroutine.payment.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.payment.api.CancelOutcome;
import com.myroutine.payment.api.PaymentApi;
import com.myroutine.support.Behavior;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.RecordedRequest;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCancelTest extends IntegrationTestSupport {

    private final PaymentApi paymentApi;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PaymentCancelTest(PaymentApi paymentApi, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.paymentApi = paymentApi;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("전액 취소하면 DONE을 반환하고 payment가 CANCELLED가 되며 cancelled_amount가 결제 금액과 같다.")
    void cancelFull() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        Long total = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);

        // When
        CancelOutcome cancelled = paymentApi.cancel(orderId, Money.of(total), "test", Ids.newId().toString());

        // Then
        assertThat(cancelled).isEqualTo(CancelOutcome.DONE);

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("CANCELLED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(total);
    }

    @Test
    @DisplayName("부분 취소를 두 번 하면 PARTIAL_CANCELLED를 거쳐 CANCELLED가 된다.")
    void cancelPartiallyTwice() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        Long total = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);
        Long partial1 = total / 3;
        Long partial2 = total - partial1;

        // When & Then
        CancelOutcome cancelled1 = paymentApi.cancel(orderId, Money.of(partial1), "partial1", Ids.newId().toString());

        assertThat(cancelled1).isEqualTo(CancelOutcome.DONE);

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("PARTIAL_CANCELLED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(partial1);

        CancelOutcome cancelled2 = paymentApi.cancel(orderId, Money.of(partial2), "partial2", Ids.newId().toString());

        assertThat(cancelled2).isEqualTo(CancelOutcome.DONE);

        payment = jdbcTemplate.queryForMap("SELECT status, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("CANCELLED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(total);
    }

    @Test
    @DisplayName("같은 키로 부분 취소를 두 번 해도 PG 취소 요청은 1건이고 두 번째도 DONE을 반환하며 cancelled_amount는 한 번만 증가한다.")
    void cancelTwiceWithSameKey() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        Long total = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);
        Long partial = total / 3;

        String idempotencyKey = Ids.newId().toString();

        // When & Then
        CancelOutcome cancelled1 = paymentApi.cancel(orderId, Money.of(partial), "test", idempotencyKey);
        CancelOutcome cancelled2 = paymentApi.cancel(orderId, Money.of(partial), "test", idempotencyKey);

        assertThat(cancelled1).isEqualTo(CancelOutcome.DONE);
        assertThat(cancelled2).isEqualTo(CancelOutcome.DONE);

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, payment_key, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("PARTIAL_CANCELLED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(partial);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment.payment_cancel WHERE idempotency_key = ?", Integer.class, idempotencyKey);
        assertThat(count).isEqualTo(1);

        String paymentKey = (String) payment.get("payment_key");
        List<RecordedRequest> requests = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("취소가 타임아웃 나면 UNKNOWN이고 같은 키로 다시 취소하면 DONE이며 cancelled_amount는 한 번만 증가한다.")
    void cancelTimeoutThenRetryWithSameKey() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        Long total = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);

        String idempotencyKey = Ids.newId().toString();

        String paymentKey = jdbcTemplate.queryForObject("SELECT payment_key FROM payment.payment WHERE order_id = ?", String.class, orderId);
        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        // When & Then
        CancelOutcome cancelled1 = paymentApi.cancel(orderId, Money.of(total), "total", idempotencyKey);

        assertThat(cancelled1).isEqualTo(CancelOutcome.UNKNOWN);

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("APPROVED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(0L);

        Map<String, Object> paymentCancel = jdbcTemplate.queryForMap("SELECT status FROM payment.payment_cancel WHERE idempotency_key = ?", idempotencyKey);
        assertThat(paymentCancel.get("status")).isEqualTo("UNKNOWN");

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(0));

        CancelOutcome cancelled2 = paymentApi.cancel(orderId, Money.of(total), "total", idempotencyKey);

        assertThat(cancelled2).isEqualTo(CancelOutcome.DONE);
        payment = jdbcTemplate.queryForMap("SELECT status, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("CANCELLED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(total);

        paymentCancel = jdbcTemplate.queryForMap("SELECT status FROM payment.payment_cancel WHERE idempotency_key = ?", idempotencyKey);
        assertThat(paymentCancel.get("status")).isEqualTo("DONE");

        List<RecordedRequest> requests = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(requests).hasSize(2);
    }

    @Test
    @DisplayName("PG가 취소를 거절하면 FAILED를 반환한다.")
    void cancelRejected() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        Long total = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);

        String idempotencyKey = Ids.newId().toString();

        String paymentKey = jdbcTemplate.queryForObject("SELECT payment_key FROM payment.payment WHERE order_id = ?", String.class, orderId);
        String code = "NOT_CANCELABLE_AMOUNT";
        pg.on("/v1/payments/" + paymentKey, Behavior.reject(code));

        // When & Then
        CancelOutcome cancelled = paymentApi.cancel(orderId, Money.of(total), "test", idempotencyKey);

        assertThat(cancelled).isEqualTo(CancelOutcome.FAILED);

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("APPROVED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(0L);

        Map<String, Object> paymentCancel = jdbcTemplate.queryForMap("SELECT status, failure_code FROM payment.payment_cancel WHERE idempotency_key = ?", idempotencyKey);
        assertThat(paymentCancel.get("status")).isEqualTo("FAILED");
        assertThat(paymentCancel.get("failure_code")).isEqualTo(code);

        List<RecordedRequest> requests = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("전액 취소로 CANCELLED가 된 뒤 같은 키로 다시 취소해도 예외 없이 DONE을 반환하고 PG 취소 요청은 늘지 않는다.")
    void cancelAgainWithSameKeyAfterFullCancel() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);

        Long total = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);

        String idempotencyKey = Ids.newId().toString();

        // When & Then
        CancelOutcome cancelled1 = paymentApi.cancel(orderId, Money.of(total), "test", idempotencyKey);

        assertThat(cancelled1).isEqualTo(CancelOutcome.DONE);
        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, payment_key, cancelled_amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("CANCELLED");
        assertThat(payment.get("cancelled_amount")).isEqualTo(total);

        CancelOutcome cancelled2 = paymentApi.cancel(orderId, Money.of(total), "test", idempotencyKey);

        assertThat(cancelled2).isEqualTo(CancelOutcome.DONE);

        String paymentKey = (String) payment.get("payment_key");
        List<RecordedRequest> requests = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(requests).hasSize(1);
    }
}
