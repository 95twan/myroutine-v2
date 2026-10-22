package com.myroutine.support;

import java.util.UUID;

public record MovementState(
        String type,
        int quantity,
        String refType,
        UUID refId
) {
}
