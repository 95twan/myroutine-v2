package com.myroutine.support;

import java.util.List;

public record ProductSnapshot(
        String name,
        String status,
        long price,
        boolean subscribable,
        int received,
        long version,
        List<PriceChangeState> priceChanges,
        List<MovementState> movements
) {
    public List<MovementState> movements(String type) {
        return movements.stream()
                .filter(m -> m.type().equals(type))
                .toList();
    }
}
