package com.myroutine.common;

import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxMetrics;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.order.application.OrderExpirationService;
import com.myroutine.order.application.OrderMetrics;
import com.myroutine.order.web.CancelLineRequest;
import com.myroutine.payment.application.PaymentMetrics;
import com.myroutine.support.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MetricsTest extends IntegrationTestSupport {

    private final MeterRegistry registry;
    private final JdbcTemplate jdbcTemplate;
    private final TestFixtures testFixtures;
    private final TestKafka testKafka;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final OutboxMetrics outboxMetrics;
    private final OrderMetrics orderMetrics;
    private final PaymentMetrics paymentMetrics;
    private final OrderExpirationService orderExpirationService;

    @Autowired
    public MetricsTest(
            MeterRegistry registry,
            JdbcTemplate jdbcTemplate,
            TestFixtures testFixtures,
            TestKafka testKafka,
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            OutboxMetrics outboxMetrics,
            OrderMetrics orderMetrics,
            PaymentMetrics paymentMetrics,
            OrderExpirationService orderExpirationService
    ) {
        this.registry = registry;
        this.jdbcTemplate = jdbcTemplate;
        this.testFixtures = testFixtures;
        this.testKafka = testKafka;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.outboxMetrics = outboxMetrics;
        this.orderMetrics = orderMetrics;
        this.paymentMetrics = paymentMetrics;
        this.orderExpirationService = orderExpirationService;
    }

    private void insertOutbox(UUID id, String status, int attempts, Instant createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO common.outbox_event
                            (id, aggregate_type, aggregate_id, event_type, topic, payload, status, attempts, next_attempt_at, created_at, updated_at)
                        VALUES (?, 'TEST', ?, 'TestEvent', 'test.metrics.v1', '{}'::jsonb, ?, ?, now(), ?, now())
                        """,
                id, Ids.newId(), status, attempts, Timestamp.from(createdAt));
    }

    private double counterValue(String name, String tagKey, String tagValue) {
        Counter counter = registry.find(name).tag(tagKey, tagValue).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("outbox 수집기가 READY 건수·가장 오래된 READY의 나이·DEAD 건수를 Gauge로 내놓는다.")
    void outboxMetricsExposeReadyAgeAndDeadGauges() {
        // Given
        UUID rowA = Ids.newId();
        insertOutbox(rowA, "READY", 0, Instant.now().minus(Duration.ofSeconds(120)));
        insertOutbox(Ids.newId(), "READY", 0, Instant.now());
        insertOutbox(Ids.newId(), "READY", 0, Instant.now());
        insertOutbox(Ids.newId(), "DEAD", 10, Instant.now());

        // When
        outboxMetrics.refresh();

        // Then
        assertThat(registry.get("outbox_ready_count").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("outbox_oldest_age_seconds").gauge().value()).isGreaterThanOrEqualTo(120.0).isLessThan(130.0);
        assertThat(registry.get("outbox_dead_count").gauge().value()).isEqualTo(1.0);

        // When
        jdbcTemplate.update("UPDATE common.outbox_event SET status = 'SENT' WHERE id = ?", rowA);
        outboxMetrics.refresh();

        // Then
        assertThat(registry.get("outbox_ready_count").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("outbox_oldest_age_seconds").gauge().value()).isGreaterThanOrEqualTo(0.0).isLessThan(10.0);
    }

    @Test
    @DisplayName("DLT로 보낸 메시지를 토픽 라벨의 Counter로 센다.")
    void dltRecordIsCountedByTopic() throws Exception {
        // Given
        String topic = "test.always-fail.v1";
        double before = counterValue("kafka_dlt_records_total", "topic", topic);

        // When
        testKafka.send(topic, Ids.newId().toString(), Ids.newId(), new TestEvent("x"));

        // Then
        Eventually.await(Duration.ofSeconds(20), () -> {
            double after = registry.get("kafka_dlt_records_total").tag("topic", topic).counter().count();
            assertThat(after - before).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("PAYMENT_IN_PROGRESS로 5분을 넘긴 주문만 saga_stuck_count(order_payment)로 센다.")
    void orderPaymentSagaStuckCountUsesFiveMinuteThreshold() {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);
        testFixtures.confirmPayment(buyerId, order.orderId());

        // When
        testFixtures.setOrderUpdatedAt(order.orderId(), Instant.now().minus(Duration.ofMinutes(4)));
        orderMetrics.refresh();

        // Then
        assertThat(registry.get("saga_stuck_count").tag("type", "order_payment").gauge().value()).isEqualTo(0.0);

        // When
        testFixtures.setOrderUpdatedAt(order.orderId(), Instant.now().minus(Duration.ofMinutes(6)));
        orderMetrics.refresh();

        // Then
        assertThat(registry.get("saga_stuck_count").tag("type", "order_payment").gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("APPROVED에 10분을 넘긴 환불만 saga_stuck_count(refund)로 센다.")
    void refundSagaStuckCountUsesTenMinuteThreshold() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, productId);
        UUID lineId = testFixtures.orderLineIdOf(orderId, productId);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelLineRequest("단순 변심"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        UUID refundId = testFixtures.refundIdOf(lineId);

        // When
        testFixtures.setRefundUpdatedAt(refundId, Instant.now().minus(Duration.ofMinutes(9)));
        orderMetrics.refresh();

        // Then
        assertThat(registry.get("saga_stuck_count").tag("type", "refund").gauge().value()).isEqualTo(0.0);

        // When
        testFixtures.setRefundUpdatedAt(refundId, Instant.now().minus(Duration.ofMinutes(11)));
        orderMetrics.refresh();

        // Then
        assertThat(registry.get("saga_stuck_count").tag("type", "refund").gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("만료 잡이 주문을 만료시킬 때마다 stock_reservation_expired_total을 올리고 롤백된 건은 세지 않는다.")
    void expiredReservationCounterIgnoresRolledBackOrder() {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        for (UUID productId : new UUID[]{product1Id, product2Id, product3Id}) {
            CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);
            testFixtures.setOrderExpired(order.orderId(), Instant.now().minus(Duration.ofMinutes(1)));
        }
        testFixtures.breakReservedStock(product3Id);

        Counter existing = registry.find("stock_reservation_expired_total").counter();
        double before = existing == null ? 0 : existing.count();

        // When
        int expired = orderExpirationService.expireBatch();

        // Then
        assertThat(expired).isEqualTo(2);
        assertThat(registry.get("stock_reservation_expired_total").counter().count() - before).isEqualTo(2.0);
    }

    @Test
    @DisplayName("결제 수집기가 UNKNOWN 건수와 가장 오래된 UNKNOWN의 created_at 기준 나이를 Gauge로 내놓는다.")
    void paymentMetricsExposeUnknownCountAndAgeFromCreatedAt() {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        CheckoutResult unknownOrder = testFixtures.createPendingOrder(buyerId, productId);
        testFixtures.confirmPayment(buyerId, unknownOrder.orderId());

        pg.on("/v1/payments/confirm", Behavior.approve());
        testFixtures.createPaidOrder(buyerId, productId);

        jdbcTemplate.update("UPDATE payment.payment SET created_at = ? WHERE order_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofSeconds(90))), unknownOrder.orderId());

        // When
        paymentMetrics.refresh();

        // Then
        assertThat(registry.get("payment_unknown_count").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("payment_unknown_oldest_age_seconds").gauge().value()).isGreaterThanOrEqualTo(90.0).isLessThan(100.0);
    }
}
