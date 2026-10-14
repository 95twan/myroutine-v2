package com.myroutine.payment.infrastructure;

import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.payment.domain.PgResult;
import com.myroutine.support.Behavior;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TossClientTest extends IntegrationTestSupport {

    private final TossClient tossClient;

    @Autowired
    public TossClientTest(TossClient tossClient) {
        this.tossClient = tossClient;
    }

    @Test
    @DisplayName("승인 응답을 Approved로 분류하고 금액과 paymentKey가 일치한다.")
    void confirmApproved() {
        // Given

        // When
        PgResult result = tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // Then
        assertThat(result).isInstanceOf(PgResult.Approved.class);
        PgResult.Approved approved = (PgResult.Approved) result;
        assertThat(approved.paymentKey()).isEqualTo("pk_1");
        assertThat(approved.totalAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("카드사 거절 응답을 Rejected로 분류한다.")
    void confirmRejected() {
        // Given
        pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"));

        // When
        PgResult result = tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // Then
        assertThat(result).isInstanceOf(PgResult.Rejected.class);
        assertThat(((PgResult.Rejected) result).code()).isEqualTo("REJECT_CARD_COMPANY");
    }

    @Test
    @DisplayName("PG 5xx 응답을 Unknown으로 분류한다.")
    void confirmServerError() {
        // Given
        pg.on("/v1/payments/confirm", Behavior.serverError());

        // When
        PgResult result = tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // Then
        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("read-timeout을 초과하면 Unknown으로 분류하고 가짜 PG 서버에는 승인 기록이 남아 있다.")
    void confirmReadTimeoutAfterPgApproved() {
        // Given
        pg.on("/v1/payments/confirm", Behavior.delay(1000));

        // When
        PgResult result = tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // Then
        assertThat(result).isInstanceOf(PgResult.Unknown.class);
        PgResult success = tossClient.queryByOrderId(Ids.newId(), "order-1");
        assertThat(success).isInstanceOf(PgResult.Approved.class);
        PgResult.Approved approved = (PgResult.Approved) success;
        assertThat(approved.paymentKey()).isEqualTo("pk_1");
        assertThat(approved.totalAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("응답 없이 연결이 끊기면 Unknown으로 분류한다.")
    void confirmConnectionDropped() {
        // Given
        pg.on("/v1/payments/confirm", Behavior.drop());

        // When
        PgResult result = tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // Then
        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("이미 처리된 결제(ALREADY_PROCESSED_PAYMENT)는 Rejected가 아니라 Unknown으로 분류한다.")
    void confirmAlreadyProcessedPayment() {
        // Given
        pg.on("/v1/payments/confirm", Behavior.reject("ALREADY_PROCESSED_PAYMENT"));

        // When
        PgResult result = tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // Then
        assertThat(result).isInstanceOf(PgResult.Unknown.class);
    }

    @Test
    @DisplayName("승인된 주문을 조회하면 Approved로 분류한다.")
    void queryApprovedOrder() {
        // Given
        tossClient.confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000));

        // When
        PgResult result = tossClient.queryByOrderId(Ids.newId(), "order-1");

        // Then
        assertThat(result).isInstanceOf(PgResult.Approved.class);
    }

    @Test
    @DisplayName("승인된 적 없는 주문을 조회하면 Rejected(NOT_FOUND_PAYMENT)로 분류한다.")
    void queryNotFoundOrder() {
        // Given

        // When
        PgResult result = tossClient.queryByOrderId(Ids.newId(), "order-1");

        // Then
        assertThat(result).isInstanceOf(PgResult.Rejected.class);
        assertThat(((PgResult.Rejected) result).code()).isEqualTo("NOT_FOUND_PAYMENT");
    }

    @Test
    @DisplayName("요청 헤더에 Idempotency-Key(paymentId)와 Basic 인증 헤더가 실린다.")
    void requestHeaders() {
        UUID paymentId = Ids.newId();

        // When
        tossClient.confirm(paymentId, "pk_1", "order-1", Money.of(1000));

        // Then
        RecordedRequest request = pg.requests("/v1/payments/confirm").get(0);
        assertThat(request.headers().get("idempotency-key")).isEqualTo(paymentId.toString());
        assertThat(request.headers().get("authorization")).startsWith("Basic ");
    }
}
