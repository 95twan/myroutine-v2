package com.myroutine.support;

import java.util.UUID;

public record PayoutCall(
        UUID settlementId,
        UUID shopId,
        long amount
) {
}
