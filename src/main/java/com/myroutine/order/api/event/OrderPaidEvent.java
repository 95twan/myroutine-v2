package com.myroutine.order.api.event;

import java.util.List;
import java.util.UUID;

public record OrderPaidEvent(
        UUID orderId,
        UUID memberId,
        long totalAmount,
        List<PaidShopOrder> shopOrders
) {

}
