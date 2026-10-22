package com.myroutine.support;

import java.time.Instant;

public record ShopSnapshot(
        String name,
        String email,
        String phone,
        String address,
        String status,
        String businessNumber,
        Instant closedAt
) {
}
