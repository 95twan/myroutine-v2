package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.order.web.CancelLineRequest;
import com.myroutine.settlement.application.SettlementService;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WithdrawTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final MockMvc mockMvc;
    private final OutboxAssert outboxAssert;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FakePayoutGateway payoutGateway;
    private final SettlementService settlementService;
    private final OutboxRelay outboxRelay;

    @Autowired
    public WithdrawTest(
            TestFixtures testFixtures,
            MockMvc mockMvc,
            OutboxAssert outboxAssert,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            FakePayoutGateway payoutGateway, SettlementService settlementService, OutboxRelay outboxRelay
    ) {
        this.testFixtures = testFixtures;
        this.mockMvc = mockMvc;
        this.outboxAssert = outboxAssert;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.payoutGateway = payoutGateway;
        this.settlementService = settlementService;
        this.outboxRelay = outboxRelay;
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (운영 중인 가게, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawActiveShopFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        testFixtures.openShop(memberId, "1111111111");

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_SHOP"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (결제 대기 주문, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawPendingPaymentOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        testFixtures.createPendingOrder(memberId, productId);

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_ORDER"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (결제 진행 중 주문, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawPaymentInProgressOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");

        pg.on("/v1/payments/confirm", Behavior.delay(1000));

        CheckoutResult order = testFixtures.createPendingOrder(memberId, productId);
        testFixtures.confirmPayment(memberId, order.orderId());

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_ORDER"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (PAID 가게주문, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawPaidShopOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        testFixtures.createPaidOrder(memberId, productId);

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_ORDER"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (SHIPPED 가게주문, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawShippedShopOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID orderId = testFixtures.createPaidOrder(memberId, productId);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        testFixtures.shipShopOrder(sellerId, shopId, shopOrderId);

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_ORDER"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (DELIVERED 가게주문, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawDeliveredShopOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID orderId = testFixtures.createPaidOrder(memberId, productId);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        testFixtures.shipShopOrder(sellerId, shopId, shopOrderId);
        testFixtures.deliverShopOrder(sellerId, shopId, shopOrderId);

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_ORDER"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("취소된 주문만 있으면 탈퇴한다.")
    void withdrawCancelledOrderSucceeds() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(memberId, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.orderId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.memberSnapshotOf(memberId).status()).isEqualTo("WITHDRAWN");
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
    }

    @Test
    @DisplayName("COMPLETED 가게주문만 있으면 탈퇴한다.")
    void withdrawCompletedShopOrderSucceeds() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID orderId = testFixtures.createPaidOrder(memberId, productId);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        jdbcTemplate.update("UPDATE orders.shop_order SET status = 'COMPLETED' WHERE id = ?", shopOrderId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.memberSnapshotOf(memberId).status()).isEqualTo("WITHDRAWN");
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
    }

    @Test
    @DisplayName("CANCELLED 가게주문만 있으면 탈퇴한다.")
    void withdrawCancelledShopOrderSucceeds() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID orderId = testFixtures.createPaidOrder(memberId, productId);

        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);

        jdbcTemplate.update("UPDATE orders.shop_order SET status = 'CANCELLED' WHERE id = ?", shopOrderId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.memberSnapshotOf(memberId).status()).isEqualTo("WITHDRAWN");
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (진행 중 환불, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawRefundInProgressFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID orderId = testFixtures.createPaidOrder(memberId, productId);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        UUID lineId = testFixtures.orderLineIdOf(orderId, productId);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        jdbcTemplate.update("UPDATE orders.shop_order SET status = 'COMPLETED' WHERE id = ?", shopOrderId);

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("ACTIVE_ORDER"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("환불이 COMPLETED이면 탈퇴한다.")
    void withdrawCompletedRefundSucceeds() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID orderId = testFixtures.createPaidOrder(memberId, productId);

        String paymentKey = testFixtures.snapshotOf(orderId).payment().paymentKey();
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shopId);
        UUID lineId = testFixtures.orderLineIdOf(orderId, productId);

        CancelLineRequest request = new CancelLineRequest("단순 변심");

        pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000));

        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/cancel", orderId, lineId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();

        String refundId = JsonPath.read(result.getResponse().getContentAsString(), "$.refundId");

        jdbcTemplate.update("UPDATE orders.shop_order SET status = 'COMPLETED' WHERE id = ?", shopOrderId);
        jdbcTemplate.update("UPDATE orders.refund SET status = 'COMPLETED' WHERE id = ?", UUID.fromString(refundId));

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.memberSnapshotOf(memberId).status()).isEqualTo("WITHDRAWN");
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (미정산 품목, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawUnsettledItemFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);
        testFixtures.insertSettlementItem(shopId, 1000, Instant.now());

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("PENDING_SETTLEMENT"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (PAYOUT_FAILED 정산, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawPayoutFailedSettlementFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);
        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItem(shopId, 1000, confirmedAt);
        payoutGateway.failNext(1);
        settlementService.run(YearMonth.of(2026, 9));

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andExpect(jsonPath("$.details.reasons", hasSize(1)))
                .andExpect(jsonPath("$.details.reasons[0]").value("PENDING_SETTLEMENT"));

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("정산이 PAID이면 탈퇴한다.")
    void withdrawPaidSettlementSucceeds() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);
        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItem(shopId, 1000, confirmedAt);
        settlementService.run(YearMonth.of(2026, 9));

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.memberSnapshotOf(memberId).status()).isEqualTo("WITHDRAWN");
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
    }

    @Test
    @DisplayName("품목 없이 CLOSED 가게만 있으면 탈퇴한다.")
    void withdrawClosedShopWithoutItemsSucceeds() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.memberSnapshotOf(memberId).status()).isEqualTo("WITHDRAWN");
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (이유가 둘 이상이면 reasons에 모두 담는다, MEMBER_HAS_ACTIVE_RESOURCES)")
    void withdrawMultipleReasonsFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        testFixtures.openShop(memberId, "1111111111");
        UUID shop2Id = testFixtures.openShop(memberId, "2222222222");
        testFixtures.closeShop(shop2Id);
        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItem(shop2Id, 1000, confirmedAt);

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shop3Id = testFixtures.openShop(sellerId, "3333333333");
        UUID productId = testFixtures.registerProduct(sellerId, shop3Id, 1000, 10);

        testFixtures.createPendingOrder(memberId, productId);

        MemberSnapshot before = testFixtures.memberSnapshotOf(memberId);

        // When & Then
        MvcResult result = mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_RESOURCES"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> reasons = JsonPath.read(body, "$.details.reasons");

        assertThat(reasons).containsExactlyInAnyOrder("ACTIVE_SHOP", "ACTIVE_ORDER", "PENDING_SETTLEMENT");
        assertThat(reasons.get(0)).isEqualTo("ACTIVE_SHOP");

        assertThat(before).isEqualTo(testFixtures.memberSnapshotOf(memberId));
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(0);
    }

    @Test
    @DisplayName("탈퇴하면 개인정보가 마스킹되고 배송지가 삭제되며 탈퇴 이벤트가 발행된다.")
    void withdrawMasksMemberAndPublishesEvent() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        testFixtures.address(memberId);
        testFixtures.address(memberId);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        MemberSnapshot s = testFixtures.memberSnapshotOf(memberId);
        assertThat(s.email()).isEqualTo("withdrawn+" + memberId + "@deleted.invalid");
        assertThat(s.nickname()).isEqualTo("탈퇴-" + memberId.toString().replace("-", "").substring(15));
        assertThat(s.name()).isEqualTo("탈퇴회원");
        assertThat(s.phone()).isNull();
        assertThat(s.passwordHash()).isNull();
        assertThat(s.withdrawnAt()).isNotNull();
        assertThat(s.addresses()).hasSize(0);

        List<OutboxSnapshot> rows = outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN);
        assertThat(rows).hasSize(1);

        assertThat(rows.get(0).aggregateType()).isEqualTo("MEMBER");
        assertThat(rows.get(0).aggregateId()).isEqualTo(memberId);
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(memberId.toString());
    }

    @Test
    @DisplayName("탈퇴 이벤트를 소비하면 장바구니가 비워진다.")
    void withdrawEventClearsCart() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");
        testFixtures.addToCart(memberId, productId, 1);

        UUID otherId = testFixtures.signup("other@test.com");
        testFixtures.addToCart(otherId, productId, 1);

        outboxAssert.clear();

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        List<OutboxSnapshot> rows = outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN);
        UUID eventId = rows.get(0).id();

        // When
        int count = outboxRelay.relayOnce();

        // Then
        assertThat(count).isEqualTo(1);
        Eventually.await(Duration.ofSeconds(5), () ->
                assertThat(testFixtures.processedCountOf("order.member-withdrawn", eventId)).isEqualTo(1)
        );
        CartSnapshot s = testFixtures.cartSnapshotOf(memberId);
        assertThat(s.cartCount()).isEqualTo(0);
        assertThat(s.itemCount()).isEqualTo(0);

        s = testFixtures.cartSnapshotOf(otherId);
        assertThat(s.cartCount()).isEqualTo(1);
        assertThat(s.itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("장바구니가 없는 회원의 탈퇴 이벤트를 소비한다.")
    void withdrawEventWithoutCartSucceeds() throws Exception {
        // Given
        String token = testFixtures.token("member@test.com");

        outboxAssert.clear();

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        List<OutboxSnapshot> rows = outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN);
        UUID eventId = rows.get(0).id();

        // When
        int count = outboxRelay.relayOnce();

        // Then
        assertThat(count).isEqualTo(1);
        Eventually.await(Duration.ofSeconds(5), () ->
                assertThat(testFixtures.processedCountOf("order.member-withdrawn", eventId)).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("같은 회원의 탈퇴 요청 두 건 중 하나를 실패한다. (INVALID_STATE_TRANSITION)")
    void withdrawConcurrentRequestsOneFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        String token = testFixtures.login("member@test.com");

        Callable<MvcResult> task = () -> mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        // When
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(2, task);

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsExactlyInAnyOrder(204, 409);

        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 409) {
                String code = objectMapper.readTree(response.getResponse().getContentAsString()).get("code").asString();
                assertThat(code).isEqualTo("INVALID_STATE_TRANSITION");
            }
        }

        assertThat(outboxAssert.rows(MemberTopics.MEMBER_WITHDRAWN)).hasSize(1);
        MemberSnapshot s = testFixtures.memberSnapshotOf(memberId);
        assertThat(s.status()).isEqualTo("WITHDRAWN");
        assertThat(s.email()).isEqualTo("withdrawn+" + memberId + "@deleted.invalid");
    }

    @Test
    @DisplayName("탈퇴한 회원은 로그인을 실패한다. (LOGIN_FAILED)")
    void loginAfterWithdrawFails() throws Exception {
        // Given
        String token = testFixtures.token("member@test.com");

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        LoginRequest request = new LoginRequest("member@test.com", "pass1234");

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("탈퇴한 회원의 이메일로 다시 가입한다.")
    void signupWithWithdrawnEmailSucceeds() throws Exception {
        // Given
        String token = testFixtures.token("member@test.com");

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        SignupRequest request = new SignupRequest("member@test.com", "pass1234", "member", "테스터");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member.member WHERE email = 'member@test.com'", Integer.class)).isEqualTo(1);
    }
}
