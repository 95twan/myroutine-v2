package com.myroutine.e2e;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.notification.application.NotificationDispatcher;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class KafkaOutageE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final JdbcTemplate jdbcTemplate;
    private final OutboxRelay outboxRelay;
    private final NotificationDispatcher notificationDispatcher;

    @Autowired
    public KafkaOutageE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                              OutboxAssert outboxAssert, JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay,
                              NotificationDispatcher notificationDispatcher) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.jdbcTemplate = jdbcTemplate;
        this.outboxRelay = outboxRelay;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Test
    @DisplayName("Kafka가 멈춘 동안의 결제 이벤트는 outbox에 남고, 재개 후 릴레이하면 모든 consumer가 처리한다.")
    void kafkaOutageRecoversAfterRelay() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productXId = testFixtures.registerProduct(sellerId, shopId, 10000, 10);
        UUID productYId = testFixtures.registerProduct(sellerId, shopId, 20000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> {
            assertThat(testFixtures.unsentOutboxCount()).isZero();
            assertThat(testFixtures.notificationCounts().type("WELCOME")).isEqualTo(2);
        });
        notificationDispatcher.dispatchBatch();

        // 1. 중단
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            // 2. 중단 중 결제
            checkoutAndConfirm(buyerId, buyerToken, addressId, productXId);
            checkoutAndConfirm(buyerId, buyerToken, addressId, productYId);

            List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_PAID);
            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(OutboxSnapshot::status).containsOnly("READY");

            // 3. 중단 중 릴레이
            assertThat(outboxRelay.relayOnce()).isZero();

            rows = outboxAssert.rows(OrderTopics.ORDER_PAID);
            assertThat(rows).extracting(OutboxSnapshot::status).containsOnly("READY");
            assertThat(rows).extracting(OutboxSnapshot::attempts).containsExactly(1, 0);
            NotificationCounts counts = testFixtures.notificationCounts();
            assertThat(counts.type("ORDER_PAID")).isZero();
            assertThat(counts.type("SHOP_ORDER_RECEIVED")).isZero();
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        // 4. 재개
        jdbcTemplate.update("UPDATE common.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE status = 'READY'");
        assertThat(outboxRelay.relayOnce()).isEqualTo(2);

        // 5. consumer 처리 완료
        Eventually.await(Duration.ofSeconds(30), () -> {
            NotificationCounts counts = testFixtures.notificationCounts();
            assertThat(counts.type("ORDER_PAID")).isEqualTo(2);
            assertThat(counts.type("SHOP_ORDER_RECEIVED")).isEqualTo(2);
        });
        assertThat(notificationDispatcher.dispatchBatch()).isEqualTo(4);

        // 6. 불변식
        assertThat(testFixtures.unsentOutboxCount()).isZero();
        invariantChecker.checkAll();
    }

    private void checkoutAndConfirm(UUID buyerId, String buyerToken, UUID addressId, UUID productId) throws Exception {
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);
        String checkoutBody = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"%s\"],\"addressId\":\"%s\"}".formatted(cartItemId, addressId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(checkoutBody, "$.orderId");
        String pgOrderId = JsonPath.read(checkoutBody, "$.pgOrderId");
        Number totalAmount = JsonPath.read(checkoutBody, "$.totalAmount");

        mockMvc.perform(post("/api/orders/{id}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"pk_outage_%s\",\"pgOrderId\":\"%s\",\"amount\":%d}".formatted(orderId, pgOrderId, totalAmount.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
