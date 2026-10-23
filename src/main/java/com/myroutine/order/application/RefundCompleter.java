package com.myroutine.order.application;

import com.myroutine.order.domain.*;
import com.myroutine.product.api.ProductApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundCompleter {
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final ProductApi productApi;
    private final Clock clock;
    private final OrderEventPublisher publisher;

    @Transactional
    public RefundStatus complete(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(
                () -> new IllegalStateException("환불을 찾을 수 없습니다. refundId=" + refundId)
        );
        if (!refund.readyToComplete()) {
            return refund.getStatus();
        }
        Order order = orderRepository.findByIdForUpdate(refund.getOrderId()).orElseThrow(
                () -> new IllegalStateException("주문을 찾을 수 없습니다. orderId=" + refund.getOrderId())
        );
        OrderLine line = order.findLine(refund.getOrderLineId());
        productApi.restore(refund.getOrderId(), line.getProductId(), line.getQuantity(), refund.getId());

        Instant now = clock.instant();

        order.applyRefund(line.getId(), refund.getAmount(), refund.getReasonType(), now);

        refund.complete(now);

        publisher.lineRefunded(refund, order, line);

        return refund.getStatus();
    }
}
