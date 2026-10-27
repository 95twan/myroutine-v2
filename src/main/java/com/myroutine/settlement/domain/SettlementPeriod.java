package com.myroutine.settlement.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public record SettlementPeriod(
        LocalDate start,
        LocalDate end
) {
    public static SettlementPeriod of(YearMonth month) {
        return new SettlementPeriod(month.atDay(1), month.atEndOfMonth());
    }

    public Instant fromInclusive(ZoneId zone) {
        return start.atStartOfDay(zone).toInstant();
    }

    public Instant toExclusive(ZoneId zone) {
        return end.plusDays(1).atStartOfDay(zone).toInstant();
    }

}
