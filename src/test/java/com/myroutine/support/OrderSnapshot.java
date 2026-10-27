package com.myroutine.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 주문 하나의 현재 상태를 DB에서 한 번에 읽은 값. 테스트가 단언 한 줄로 확인하게 한다.
 * 결제가 아직 없으면 payment는 null이다.
 */
public record OrderSnapshot(
        Map<UUID, LineState> linesByProduct,
        List<String> shopOrderStatuses,
        List<String> refundStatuses,
        PaymentState payment,
        String status
) {

    public LineState line(UUID productId) {
        return linesByProduct.get(productId);
    }

    /** 가게주문이 하나인 주문에서 그 상태를 읽는다. */
    public String shopOrderStatus() {
        if (shopOrderStatuses.size() != 1) {
            throw new IllegalStateException("가게주문이 하나가 아닙니다. count=" + shopOrderStatuses.size());
        }
        return shopOrderStatuses.get(0);
    }

    public record LineState(String status, long refundedAmount, Instant confirmedAt) {
    }

    public record PaymentState(long amount, long cancelledAmount, String status, String paymentKey) {
    }
}
