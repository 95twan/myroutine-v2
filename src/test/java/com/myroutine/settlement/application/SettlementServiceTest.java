package com.myroutine.settlement.application;

import com.myroutine.common.model.Ids;
import com.myroutine.settlement.domain.SettlementRepository;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.SettlementSnapshot;
import com.myroutine.support.TestFixtures;
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

    @MockitoSpyBean
    private SettlementRepository settlementRepository;

    @Autowired
    public SettlementServiceTest(TestFixtures testFixtures, JdbcTemplate jdbcTemplate, SettlementService settlementService) {
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.settlementService = settlementService;
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
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement.settlement WHERE gross_amount = 15000 AND item_count = 2 AND fee_rate_bp = 500 AND period_start = DATE '2026-09-01' AND period_end = DATE '2026-09-30' AND status = 'CALCULATED'", Integer.class);
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
}
