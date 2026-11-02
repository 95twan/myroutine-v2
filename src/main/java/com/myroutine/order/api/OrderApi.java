package com.myroutine.order.api;

import java.util.UUID;

public interface OrderApi {
    ConfirmedLineInfo getConfirmedLine(UUID memberId, UUID orderLineId);
}
