package com.myroutine.payment.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.payment.api.*;
import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentApiImplTest extends IntegrationTestSupport {

    private final PaymentApi paymentApi;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PaymentApiImplTest(PaymentApi paymentApi, JdbcTemplate jdbcTemplate) {
        this.paymentApi = paymentApi;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("IN_PROGRESS 결제에 승인 결과를 반영하면 APPROVED가 된다.")
    void applyOutcomeApproved() {
        // Given
        UUID orderId = Ids.newId();
        UUID paymentId = paymentApi.begin(new StartPayment(
                orderId,
                Ids.newId(),
                Money.of(43000),
                "ORD-" + orderId,
                "pk_" + orderId)
        );

        PgOutcome outcome = new PgOutcome(PgOutcomeType.APPROVED, null, "카드", Instant.now());

        // When
        PaymentState paymentState = paymentApi.applyOutcome(paymentId, outcome);

        // Then
        assertThat(paymentState).isEqualTo(PaymentState.APPROVED);
        Map<String, Object> result = jdbcTemplate.queryForMap("SELECT status, method, approved_at FROM payment.payment WHERE id = ?", paymentId);
        assertThat(result.get("status")).isEqualTo("APPROVED");
        assertThat(result.get("method")).isEqualTo("카드");
        assertThat(result.get("approved_at")).isNotNull();
    }

    @Test
    @DisplayName("IN_PROGRESS 결제에 거절 결과를 반영하면 FAILED가 되고 거절 코드가 남는다.")
    void applyOutcomeRejected() {
        // Given
        UUID orderId = Ids.newId();
        UUID paymentId = paymentApi.begin(new StartPayment(
                orderId,
                Ids.newId(),
                Money.of(43000),
                "ORD-" + orderId,
                "pk_" + orderId)
        );

        PgOutcome outcome = new PgOutcome(PgOutcomeType.REJECTED, "REJECT_CARD_COMPANY", null, null);

        // When
        PaymentState paymentState = paymentApi.applyOutcome(paymentId, outcome);

        // Then
        assertThat(paymentState).isEqualTo(PaymentState.FAILED);
        Map<String, Object> result = jdbcTemplate.queryForMap("SELECT status, failure_code, method, approved_at FROM payment.payment WHERE id = ?", paymentId);
        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(result.get("failure_code")).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(result.get("method")).isNull();
        assertThat(result.get("approved_at")).isNull();
    }

    @Test
    @DisplayName("이미 APPROVED인 결제에 거절 결과를 반영해도 예외 없이 APPROVED 그대로다.")
    void applyOutcomeRejectedAfterApproved() {
        // Given
        UUID orderId = Ids.newId();
        UUID paymentId = paymentApi.begin(new StartPayment(
                orderId,
                Ids.newId(),
                Money.of(43000),
                "ORD-" + orderId,
                "pk_" + orderId)
        );

        PgOutcome outcome = new PgOutcome(PgOutcomeType.APPROVED, null, "카드", Instant.now());
        paymentApi.applyOutcome(paymentId, outcome);

        PgOutcome reject = new PgOutcome(PgOutcomeType.REJECTED, "REJECT_CARD_COMPANY", null, null);

        // When
        PaymentState paymentState = paymentApi.applyOutcome(paymentId, reject);

        // Then
        assertThat(paymentState).isEqualTo(PaymentState.APPROVED);
        Map<String, Object> result = jdbcTemplate.queryForMap("SELECT status, failure_code FROM payment.payment WHERE id = ?", paymentId);
        assertThat(result.get("status")).isEqualTo("APPROVED");
        assertThat(result.get("failure_code")).isNull();
    }

    @Test
    @DisplayName("같은 orderId로 결제를 두 번 시작하기를 실패한다. (uk_payment_order 위반)")
    void beginTwiceWithSameOrderId() {
        // Given
        UUID orderId = Ids.newId();

        paymentApi.begin(new StartPayment(
                orderId,
                Ids.newId(),
                Money.of(43000),
                "ORD-" + orderId,
                "pk_" + orderId)
        );

        // When & Then
        assertThatThrownBy(() -> paymentApi.begin(new StartPayment(
                orderId,
                Ids.newId(),
                Money.of(43000),
                "ORD-other" + orderId,
                "pk_other" + orderId)
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payment_order");
    }
}
