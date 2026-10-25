package com.myroutine.settlement.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository {
    int insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int bp, Instant now);

    Optional<Settlement> findById(UUID id);

    List<UUID> findIdsToPay(Collection<SettlementStatus> statuses, UUID afterId, Limit limit);
}
