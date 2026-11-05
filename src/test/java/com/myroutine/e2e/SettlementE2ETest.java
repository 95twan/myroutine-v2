package com.myroutine.e2e;

import com.myroutine.common.config.BusinessProperties;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.notification.application.NotificationDispatcher;
import com.myroutine.settlement.api.event.SettlementTopics;
import com.myroutine.settlement.application.SettlementRunResult;
import com.myroutine.settlement.application.SettlementService;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SettlementE2ETest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final InvariantChecker invariantChecker;
    private final OutboxAssert outboxAssert;
    private final FakePayoutGateway fakePayoutGateway;
    private final OutboxRelay outboxRelay;
    private final SettlementService settlementService;
    private final NotificationDispatcher notificationDispatcher;
    private final BusinessProperties businessProperties;

    @Autowired
    public SettlementE2ETest(MockMvc mockMvc, TestFixtures testFixtures, InvariantChecker invariantChecker,
                             OutboxAssert outboxAssert, FakePayoutGateway fakePayoutGateway, OutboxRelay outboxRelay,
                             SettlementService settlementService, NotificationDispatcher notificationDispatcher,
                             BusinessProperties businessProperties) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.invariantChecker = invariantChecker;
        this.outboxAssert = outboxAssert;
        this.fakePayoutGateway = fakePayoutGateway;
        this.outboxRelay = outboxRelay;
        this.settlementService = settlementService;
        this.notificationDispatcher = notificationDispatcher;
        this.businessProperties = businessProperties;
    }

    @Test
    @DisplayName("여러 가게의 구매확정 품목을 정산 잡으로 두 번 돌려도 정산과 지급은 가게마다 1회다.")
    void settlementRunTwiceSettlesAndPaysOnce() throws Exception {
        // Given
        UUID sellerAId = testFixtures.signup("seller-a@test.com");
        UUID sellerBId = testFixtures.signup("seller-b@test.com");
        UUID sellerCId = testFixtures.signup("seller-c@test.com");
        UUID shopAId = testFixtures.openShop(sellerAId, "1111111111");
        UUID shopBId = testFixtures.openShop(sellerBId, "2222222222");
        UUID shopCId = testFixtures.openShop(sellerCId, "3333333333");
        UUID productAId = testFixtures.registerProduct(sellerAId, shopAId, 10000, 10);
        UUID productBId = testFixtures.registerProduct(sellerBId, shopBId, 20000, 10);
        UUID productCId = testFixtures.registerProduct(sellerCId, shopCId, 30000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        String buyerToken = testFixtures.login("buyer@test.com");

        UUID orderAId = testFixtures.createDeliveredOrder(buyerId, sellerAId, shopAId, productAId);
        UUID orderBId = testFixtures.createDeliveredOrder(buyerId, sellerBId, shopBId, productBId);
        UUID orderCId = testFixtures.createDeliveredOrder(buyerId, sellerCId, shopCId, productCId);
        confirmLine(buyerToken, orderAId, testFixtures.orderLineIdOf(orderAId, productAId));
        confirmLine(buyerToken, orderBId, testFixtures.orderLineIdOf(orderBId, productBId));
        confirmLine(buyerToken, orderCId, testFixtures.orderLineIdOf(orderCId, productCId));

        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10), () -> assertThat(testFixtures.unsettledItemCount()).isEqualTo(3));

        // 1. 첫 실행
        YearMonth month = YearMonth.from(testFixtures.snapshotOf(orderAId).line(productAId).confirmedAt().atZone(businessProperties.businessZone()));
        SettlementRunResult first = settlementService.run(month);

        assertThat(first.settledShops()).isEqualTo(3);
        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(3);
        SettlementSnapshot settlementA = testFixtures.settlementSnapshotOf(shopAId, month);
        assertThat(settlementA.grossAmount()).isEqualTo(10000);
        assertThat(settlementA.status()).isEqualTo("PAID");
        SettlementSnapshot settlementB = testFixtures.settlementSnapshotOf(shopBId, month);
        assertThat(settlementB.grossAmount()).isEqualTo(20000);
        assertThat(settlementB.status()).isEqualTo("PAID");
        SettlementSnapshot settlementC = testFixtures.settlementSnapshotOf(shopCId, month);
        assertThat(settlementC.grossAmount()).isEqualTo(30000);
        assertThat(settlementC.status()).isEqualTo("PAID");
        assertThat(testFixtures.unsettledItemCount()).isZero();
        assertThat(fakePayoutGateway.calls()).hasSize(3);
        assertThat(outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID)).hasSize(3);

        // 2. 두 번째 실행
        SettlementRunResult second = settlementService.run(month);

        assertThat(second.settledShops()).isZero();
        assertThat(fakePayoutGateway.calls()).hasSize(3);
        assertThat(outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID)).hasSize(3);

        // 3. 지급 알림
        outboxRelay.relayOnce();
        Eventually.await(Duration.ofSeconds(10),
                () -> assertThat(testFixtures.notificationCounts().type("SETTLEMENT_PAID")).isEqualTo(3));
        notificationDispatcher.dispatchBatch();

        // 4. 불변식
        invariantChecker.checkAll();
    }

    private void confirmLine(String buyerToken, UUID orderId, UUID lineId) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/lines/{lineId}/confirm", orderId, lineId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
