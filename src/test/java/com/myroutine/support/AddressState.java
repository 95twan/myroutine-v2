package com.myroutine.support;

import java.util.UUID;

public record AddressState(
        UUID id,
        String recipient,
        String address2,
        boolean isDefault
) {
}
