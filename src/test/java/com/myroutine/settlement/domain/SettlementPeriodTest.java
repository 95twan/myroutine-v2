package com.myroutine.settlement.domain;

import com.myroutine.common.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementPeriodTest {

    @Test
    @DisplayName("정산 기간의 경계 시각을 계산한다.")
    void periodBoundaries() {
        // Given
        SettlementPeriod period = SettlementPeriod.of(YearMonth.of(2026, 10));
        ZoneId zone = ZoneId.of("Asia/Seoul");

        // When
        LocalDate start = period.start();
        LocalDate end = period.end();
        Instant from = period.fromInclusive(zone);
        Instant to = period.toExclusive(zone);

        // Then
        assertThat(start).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(end).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(from).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
        assertThat(to).isEqualTo(Instant.parse("2026-10-31T15:00:00Z"));

    }

    @ParameterizedTest(name = "[성공] gross={0}, bp={1}, itemCount={2} -> fee={3}, net={4}")
    @DisplayName("수수료를 원 미만 내림으로 계산한다.")
    @MethodSource("feeProvider")
    void calculateFeeRoundsDown(long gross, int bp, int itemCount, long fee, long net) {
        // Given
        Settlement settlement = BeanUtils.instantiateClass(Settlement.class);

        // When
        settlement.calculate(Money.of(gross), bp, itemCount);

        // Then
        assertThat(settlement.getGrossAmount().amount()).isEqualTo(gross);
        assertThat(settlement.getFeeAmount().amount()).isEqualTo(fee);
        assertThat(settlement.getNetAmount().amount()).isEqualTo(net);
        assertThat(settlement.getFeeRateBp()).isEqualTo(bp);
        assertThat(settlement.getItemCount()).isEqualTo(itemCount);
    }

    public static Stream<Arguments> feeProvider() {
        return Stream.of(
                Arguments.arguments(12345, 500, 3, 617, 11728),
                Arguments.arguments(19, 500, 1, 0, 19),
                Arguments.arguments(0, 500, 0, 0, 0)
        );
    }


}
