package com.myroutine.order.application;

import java.util.UUID;

public record Started(
        UUID paymentId,
        ConfirmResult early
) {
}
