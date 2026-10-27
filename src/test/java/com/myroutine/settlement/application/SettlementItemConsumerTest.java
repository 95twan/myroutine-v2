package com.myroutine.settlement.application;

import com.myroutine.common.model.Ids;
import com.myroutine.order.api.event.OrderLineConfirmedEvent;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementItemConsumerTest extends IntegrationTestSupport {

    private final TestKafka testKafka;
    private final TestFixtures testFixtures;
    private final SettlementItemService settlementItemService;

    @Autowired
    public SettlementItemConsumerTest(TestKafka testKafka, TestFixtures testFixtures, SettlementItemService settlementItemService) {
        this.testKafka = testKafka;
        this.testFixtures = testFixtures;
        this.settlementItemService = settlementItemService;
    }

    @Test
    @DisplayName("같은 확정 이벤트를 두 번 받아도 품목은 한 번만 적재한다.")
    void duplicateEventStoresOnce() throws Exception {
        // Given
        UUID orderId = Ids.newId();
        UUID orderLine1Id = Ids.newId();
        UUID shopId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();
        OrderLineConfirmedEvent event = new OrderLineConfirmedEvent(orderLine1Id, orderId, shopId, productId, memberId, 15000, Instant.parse("2026-09-15T03:00:00Z"));

        UUID event1Id = Ids.newId();

        // When
        testKafka.send(OrderTopics.ORDER_LINE_CONFIRMED, orderId.toString(), event1Id, event);
        testKafka.send(OrderTopics.ORDER_LINE_CONFIRMED, orderId.toString(), event1Id, event);

        UUID orderLine2Id = Ids.newId();
        UUID event2Id = Ids.newId();
        event = new OrderLineConfirmedEvent(orderLine2Id, orderId, shopId, productId, memberId, 15000, Instant.parse("2026-09-15T03:00:00Z"));
        testKafka.send(OrderTopics.ORDER_LINE_CONFIRMED, orderId.toString(), event2Id, event);

        // Then
        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.settlementItemSnapshotOf(orderLine2Id)).isNotNull();
        });
        SettlementItemSnapshot snapshot = testFixtures.settlementItemSnapshotOf(orderLine1Id);
        assertThat(snapshot.shopId()).isEqualTo(shopId);
        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.productId()).isEqualTo(productId);
        assertThat(snapshot.amount()).isEqualTo(15000);
        assertThat(snapshot.confirmedAt()).isEqualTo(event.confirmedAt());
        assertThat(snapshot.settlementId()).isNull();

        assertThat(testFixtures.countOf("settlement.settlement_item")).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 eventId로 같은 품목이 와도 한 번만 적재한다.")
    void sameOrderLineWithDifferentEventIdStoresOnce() throws Exception {
        // Given
        UUID orderId = Ids.newId();
        UUID orderLine1Id = Ids.newId();
        UUID shopId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();
        OrderLineConfirmedEvent event1 = new OrderLineConfirmedEvent(orderLine1Id, orderId, shopId, productId, memberId, 15000, Instant.parse("2026-09-15T03:00:00Z"));
        OrderLineConfirmedEvent event2 = new OrderLineConfirmedEvent(orderLine1Id, orderId, shopId, productId, memberId, 99999, Instant.parse("2026-09-15T03:00:00Z"));

        // When
        settlementItemService.register(event1);
        settlementItemService.register(event2);

        // Then
        SettlementItemSnapshot snapshot = testFixtures.settlementItemSnapshotOf(orderLine1Id);
        assertThat(snapshot.amount()).isEqualTo(15000);

        assertThat(testFixtures.countOf("settlement.settlement_item")).isEqualTo(1);
    }
}
