package com.myroutine.common.outbox;

import com.myroutine.common.model.Ids;
import com.myroutine.common.web.TraceIds;
import com.myroutine.shop.api.event.ShopOpenedEvent;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OutboxAssert;
import com.myroutine.support.OutboxSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPublisherTest extends IntegrationTestSupport {

    private final OutboxPublisher outboxPublisher;
    private final TransactionTemplate tx;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;

    @Autowired
    public OutboxPublisherTest(OutboxPublisher outboxPublisher, TransactionTemplate tx, TestFixtures testFixtures, OutboxAssert outboxAssert) {
        this.outboxPublisher = outboxPublisher;
        this.tx = tx;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
    }

    @Test
    @DisplayName("트랜잭션 없이 이벤트를 발행하는 것을 실패한다. (트랜잭션 필수)")
    void publishWithoutTransactionFails() {
        // Given
        UUID shopId = Ids.newId();
        UUID memberId = Ids.newId();

        // When & Then
        assertThatThrownBy(() -> outboxPublisher.publish("shop.shop-opened.v1", "SHOP", shopId, new ShopOpenedEvent(shopId, memberId)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }

    @Test
    @DisplayName("상태 변경이 롤백되면 outbox 행도 남지 않는다.")
    void rolledBackPublishLeavesNoRow() {
        // Given
        UUID shopId = Ids.newId();
        UUID memberId = Ids.newId();

        // When & Then
        tx.execute(s -> {
            outboxPublisher.publish("shop.shop-opened.v1", "SHOP", shopId, new ShopOpenedEvent(shopId, memberId));
            s.setRollbackOnly();
            return null;
        });

        assertThat(outboxAssert.rows("shop.shop-opened.v1")).hasSize(0);
    }

    @Test
    @DisplayName("커밋하면 READY 행이 하나 저장되고 trace_id는 MDC 값이다.")
    void committedPublishStoresReadyRowWithTraceId() {
        // Given
        UUID shopId = Ids.newId();
        UUID memberId = Ids.newId();
        MDC.put(TraceIds.MDC_KEY, "trace-test-1");

        // When & Then
        try {
            tx.executeWithoutResult(s -> {
                outboxPublisher.publish("shop.shop-opened.v1", "SHOP", shopId, new ShopOpenedEvent(shopId, memberId));
            });
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }

        List<OutboxSnapshot> rows = outboxAssert.rows("shop.shop-opened.v1");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).traceId()).isEqualTo("trace-test-1");
        assertThat(rows.get(0).status()).isEqualTo("READY");
        assertThat(rows.get(0).aggregateType()).isEqualTo("SHOP");
        assertThat(rows.get(0).aggregateId()).isEqualTo(shopId);
        assertThat(rows.get(0).eventType()).isEqualTo("ShopOpenedEvent");
        assertThat(rows.get(0).topic()).isEqualTo("shop.shop-opened.v1");
        assertThat(rows.get(0).attempts()).isEqualTo(0);
        assertThat(rows.get(0).attempts()).isEqualTo(0);
        assertThat(rows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(memberId.toString());
    }

    @Test
    @DisplayName("MDC에 traceId가 없으면 trace_id는 null로 저장한다.")
    void publishWithoutMdcStoresNullTraceId() {
        // Given
        UUID shopId = Ids.newId();
        UUID memberId = Ids.newId();

        // When & Then
        tx.executeWithoutResult(s -> {
            outboxPublisher.publish("shop.shop-opened.v1", "SHOP", shopId, new ShopOpenedEvent(shopId, memberId));
        });

        List<OutboxSnapshot> rows = outboxAssert.rows("shop.shop-opened.v1");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).traceId()).isNull();
    }
}
