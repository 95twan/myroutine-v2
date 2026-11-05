package com.myroutine.e2e;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.order.application.OrderExpirationService;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AbandonedPaymentE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final OutboxRelay outboxRelay;
    private final OrderExpirationService orderExpirationService;
    private final Clock clock;

    @Autowired
    public AbandonedPaymentE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                                   OutboxAssert outboxAssert, OutboxRelay outboxRelay,
                                   OrderExpirationService orderExpirationService, Clock clock) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.outboxRelay = outboxRelay;
        this.orderExpirationService = orderExpirationService;
        this.clock = clock;
    }

    @Test
    @DisplayName("체크아웃 후 결제하지 않은 주문은 만료 잡으로 EXPIRED가 되고 재고가 원복되며 장바구니는 그대로다.")
    void abandonedPaymentExpiresAndRestoresStock() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 10000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        String checkoutBody = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"%s\"],\"addressId\":\"%s\"}".formatted(cartItemId, addressId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(checkoutBody, "$.orderId"));

        // 1. 만료 잡
        testFixtures.setOrderExpired(orderId, clock.instant().minus(Duration.ofMinutes(1)));
        assertThat(orderExpirationService.expireBatch()).isEqualTo(1);

        // 2. 주문·예약·재고·장바구니
        OrderSnapshot s = testFixtures.snapshotOf(orderId);
        assertThat(s.status()).isEqualTo("EXPIRED");
        assertThat(s.reservationStatuses()).containsExactly("EXPIRED");
        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(10);
        assertThat(stock.reserved()).isZero();
        assertThat(testFixtures.cartSnapshotOf(buyerId).quantity(productId)).isEqualTo(1);

        // 3. outbox와 알림
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_EXPIRED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).payload().get("reason").asText()).isEqualTo("EXPIRED");
        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10),
                () -> assertThat(testFixtures.notificationCounts().type("ORDER_CLOSED")).isEqualTo(1));

        // 4. 잡 재실행
        assertThat(orderExpirationService.expireBatch()).isZero();
        assertThat(outboxAssert.rows(OrderTopics.ORDER_EXPIRED)).hasSize(1);

        // 5. 불변식
        invariantChecker.checkAll();
    }
}
