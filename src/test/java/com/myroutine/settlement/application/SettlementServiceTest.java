package com.myroutine.settlement.application;

import com.myroutine.common.model.Ids;
import com.myroutine.settlement.api.event.SettlementTopics;
import com.myroutine.settlement.domain.SettlementRepository;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.willThrow;

class SettlementServiceTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;
    private final SettlementService settlementService;
    private final FakePayoutGateway payoutGateway;
    private final OutboxAssert outboxAssert;

    @MockitoSpyBean
    private SettlementRepository settlementRepository;

    @Autowired
    public SettlementServiceTest(
            TestFixtures testFixtures,
            JdbcTemplate jdbcTemplate,
            SettlementService settlementService,
            FakePayoutGateway payoutGateway,
            OutboxAssert outboxAssert
    ) {
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.settlementService = settlementService;
        this.payoutGateway = payoutGateway;
        this.outboxAssert = outboxAssert;
    }

    @Test
    @DisplayName("가게 1,200곳을 정산한다.")
    void settle1200Shops() {
        // Given
        List<UUID> shopIds = Stream.generate(Ids::newId).limit(1200).toList();
        testFixtures.insertSettlementItems(shopIds, 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        // When
        SettlementRunResult result = settlementService.run(YearMonth.of(2026, 9));

        // Then
        assertThat(result.settledShops()).isEqualTo(1200);
        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(1200);
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement.settlement WHERE gross_amount = 15000 AND item_count = 2 AND fee_rate_bp = 500 AND period_start = DATE '2026-09-01' AND period_end = DATE '2026-09-30' AND status = 'PAID'", Integer.class);
        assertThat(count).isEqualTo(1200);
        assertThat(testFixtures.unsettledItemCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("run을 두 번 실행해도 정산은 한 번만 만든다.")
    void runTwiceSettlesOnce() {
        // Given
        List<UUID> shopIds = Stream.generate(Ids::newId).limit(3).toList();
        testFixtures.insertSettlementItems(shopIds, 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        // When & Then
        SettlementRunResult first = settlementService.run(YearMonth.of(2026, 9));

        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(3);
        List<SettlementSnapshot> before = List.of(
                testFixtures.settlementSnapshotOf(shopIds.get(0)),
                testFixtures.settlementSnapshotOf(shopIds.get(1)),
                testFixtures.settlementSnapshotOf(shopIds.get(2))
        );

        SettlementRunResult second = settlementService.run(YearMonth.of(2026, 9));
        List<SettlementSnapshot> after = List.of(
                testFixtures.settlementSnapshotOf(shopIds.get(0)),
                testFixtures.settlementSnapshotOf(shopIds.get(1)),
                testFixtures.settlementSnapshotOf(shopIds.get(2))
        );

        assertThat(first.settledShops()).isEqualTo(3);
        assertThat(second.settledShops()).isEqualTo(0);
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("정산 뒤에 늦게 도착한 전월 품목은 정산에 넣지 않는다.")
    void lateItemIsNotIncluded() {
        // Given
        UUID shopId = Ids.newId();
        testFixtures.insertSettlementItems(List.of(shopId), 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        // When & Then
        settlementService.run(YearMonth.of(2026, 9));

        UUID lastLineId = testFixtures.insertSettlementItem(shopId, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        SettlementRunResult second = settlementService.run(YearMonth.of(2026, 9));

        assertThat(second.settledShops()).isEqualTo(0);
        assertThat(testFixtures.settlementSnapshotOf(shopId).grossAmount()).isEqualTo(15000);
        assertThat(testFixtures.settlementSnapshotOf(shopId).itemCount()).isEqualTo(2);
        assertThat(testFixtures.settlementItemSnapshotOf(lastLineId).settlementId()).isNull();
    }

    @Test
    @DisplayName("정산 기간 경계의 품목을 달에 맞게 나눈다.")
    void periodBoundary() {
        // Given
        UUID shopId = Ids.newId();
        testFixtures.insertSettlementItem(shopId, 1000, Instant.parse("2026-08-31T14:59:59Z"));
        testFixtures.insertSettlementItem(shopId, 2000, Instant.parse("2026-08-31T15:00:00Z"));
        testFixtures.insertSettlementItem(shopId, 4000, Instant.parse("2026-09-30T14:59:59Z"));
        UUID line4Id = testFixtures.insertSettlementItem(shopId, 8000, Instant.parse("2026-09-30T15:00:00Z"));

        // When & Then
        SettlementRunResult result1 = settlementService.run(YearMonth.of(2026, 8));
        assertThat(result1.settledShops()).isEqualTo(1);
        SettlementSnapshot snapshot = testFixtures.settlementSnapshotOf(shopId, YearMonth.of(2026, 8));
        assertThat(snapshot.grossAmount()).isEqualTo(1000);
        assertThat(snapshot.itemCount()).isEqualTo(1);

        SettlementRunResult result2 = settlementService.run(YearMonth.of(2026, 9));
        assertThat(result2.settledShops()).isEqualTo(1);
        snapshot = testFixtures.settlementSnapshotOf(shopId, YearMonth.of(2026, 9));
        assertThat(snapshot.grossAmount()).isEqualTo(6000);
        assertThat(snapshot.itemCount()).isEqualTo(2);
        assertThat(testFixtures.settlementItemSnapshotOf(line4Id).settlementId()).isNull();

        SettlementRunResult result3 = settlementService.run(YearMonth.of(2026, 10));
        assertThat(result3.settledShops()).isEqualTo(1);
        snapshot = testFixtures.settlementSnapshotOf(shopId, YearMonth.of(2026, 10));
        assertThat(snapshot.grossAmount()).isEqualTo(8000);
        assertThat(snapshot.itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 가게의 정산이 실패해도 다른 가게는 정산한다.")
    void oneShopFailureDoesNotBlockOthers() {
        // Given
        List<UUID> ids = Stream.generate(Ids::newId).limit(3).toList();
        testFixtures.insertSettlementItems(ids, 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));
        UUID badShopId = jdbcTemplate.queryForObject("SELECT shop_id FROM settlement.settlement_item ORDER BY shop_id LIMIT 1", UUID.class);
        willThrow(new RuntimeException("test"))
                .given(settlementRepository)
                .insertIfAbsent(any(), eq(badShopId), any(), any(), anyInt(), any());

        // When & Then
        SettlementRunResult result = settlementService.run(YearMonth.of(2026, 9));
        assertThat(result.settledShops()).isEqualTo(2);
        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(2);
        assertThat(testFixtures.settlementSnapshotOf(badShopId)).isNull();
        assertThat(testFixtures.unsettledItemCount()).isEqualTo(2);

        Mockito.reset(settlementRepository);

        result = settlementService.run(YearMonth.of(2026, 9));
        assertThat(result.settledShops()).isEqualTo(1);
        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(3);
        assertThat(testFixtures.settlementSnapshotOf(badShopId).grossAmount()).isEqualTo(15000);
    }

    @Test
    @DisplayName("지급에 성공하면 정산이 PAID가 되고 지급 이벤트를 발행한다.")
    void payoutSucceeds() {
        // Given
        UUID shopId = Ids.newId();
        testFixtures.insertSettlementItems(List.of(shopId), 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        // When
        settlementService.run(YearMonth.of(2026, 9));

        // Then
        SettlementSnapshot s = testFixtures.settlementSnapshotOf(shopId);
        assertThat(s.status()).isEqualTo("PAID");
        assertThat(s.payoutReference()).isNotNull();
        assertThat(s.paidAt()).isNotNull();

        assertThat(payoutGateway.calls()).hasSize(1);
        assertThat(payoutGateway.calls().get(0).settlementId()).isEqualTo(s.id());
        assertThat(payoutGateway.calls().get(0).shopId()).isEqualTo(shopId);
        assertThat(payoutGateway.calls().get(0).amount()).isEqualTo(14250);

        List<OutboxSnapshot> rows = outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateId()).isEqualTo(s.id());
        assertThat(rows.get(0).aggregateType()).isEqualTo("SETTLEMENT");
        assertThat(rows.get(0).payload().get("settlementId").asString()).isEqualTo(s.id().toString());
        assertThat(rows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(rows.get(0).payload().get("netAmount").asLong()).isEqualTo(14250);
        assertThat(rows.get(0).payload().get("periodStart").asString()).isEqualTo("2026-09-01");
        assertThat(rows.get(0).payload().get("periodEnd").asString()).isEqualTo("2026-09-30");

        settlementService.run(YearMonth.of(2026, 9));
        assertThat(payoutGateway.calls()).hasSize(1);
        assertThat(outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID)).hasSize(1);
    }

    @Test
    @DisplayName("0원 정산은 송금 없이 PAID로 만든다.")
    void zeroNetSettlementIsPaidWithoutPayout() {
        // Given
        UUID shopId = Ids.newId();
        testFixtures.insertSettlementItem(shopId, 0, Instant.parse("2026-09-15T03:00:00Z"));

        // When
        settlementService.run(YearMonth.of(2026, 9));

        // Then
        SettlementSnapshot s = testFixtures.settlementSnapshotOf(shopId);
        assertThat(s.status()).isEqualTo("PAID");
        assertThat(s.payoutReference()).isNull();
        assertThat(s.paidAt()).isNotNull();

        assertThat(payoutGateway.calls()).hasSize(0);

        List<OutboxSnapshot> rows = outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).payload().get("netAmount").asLong()).isEqualTo(0);
    }

    @Test
    @DisplayName("지급이 실패하면 PAYOUT_FAILED로 남기고 다음 실행에서 다시 지급한다.")
    void failedPayoutIsRetriedOnNextRun() {
        // Given
        payoutGateway.failNext(1);
        UUID shopId = Ids.newId();
        testFixtures.insertSettlementItem(shopId, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        // When & Then
        settlementService.run(YearMonth.of(2026, 9));

        SettlementSnapshot s = testFixtures.settlementSnapshotOf(shopId);
        assertThat(s.status()).isEqualTo("PAYOUT_FAILED");
        assertThat(s.payoutAttempts()).isEqualTo(1);
        assertThat(s.lastError()).isNotNull();
        assertThat(s.lastError().length()).isLessThanOrEqualTo(500);
        assertThat(s.payoutReference()).isNull();
        assertThat(s.paidAt()).isNull();

        assertThat(outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID)).hasSize(0);

        settlementService.run(YearMonth.of(2026, 9));
        s = testFixtures.settlementSnapshotOf(shopId);
        assertThat(s.status()).isEqualTo("PAID");
        assertThat(s.payoutAttempts()).isEqualTo(1);
        assertThat(s.payoutReference()).isNotNull();
        assertThat(s.paidAt()).isNotNull();

        assertThat(outboxAssert.rows(SettlementTopics.SETTLEMENT_PAID)).hasSize(1);
    }

    @Test
    @DisplayName("같은 정산을 두 번 지급해도 이체 참조 ID는 같다.")
    void samePayoutReferenceOnRepeatedPayout() {
        // Given
        UUID shopId = Ids.newId();
        testFixtures.insertSettlementItem(shopId, 7500, Instant.parse("2026-09-15T03:00:00Z"));

        settlementService.run(YearMonth.of(2026, 9));
        SettlementSnapshot s = testFixtures.settlementSnapshotOf(shopId);
        String refId = s.payoutReference();
        jdbcTemplate.update("UPDATE settlement.settlement SET status = 'CALCULATED', payout_reference = NULL, paid_at = NULL WHERE id = ?", s.id());

        // When & Then
        settlementService.run(YearMonth.of(2026, 9));

        s = testFixtures.settlementSnapshotOf(shopId);
        assertThat(s.status()).isEqualTo("PAID");
        assertThat(s.payoutReference()).isEqualTo(refId);

        List<PayoutCall> calls = payoutGateway.calls();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).settlementId()).isEqualTo(calls.get(1).settlementId());
    }
}
