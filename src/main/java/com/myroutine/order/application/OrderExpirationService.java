package com.myroutine.order.application;

import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ReleaseReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationService {
    private final OrderRepository orderRepository;
    private final ProductApi productApi;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final OrderEventPublisher publisher;

    public int expireBatch() {
        Instant now = clock.instant();
        List<UUID> orderIds = orderRepository.findExpiredIds(OrderStatus.PENDING_PAYMENT, now, Limit.of(100));
        int count = 0;
        for (UUID orderId : orderIds) {
            try {
                if (tx.execute(s -> expireOne(orderId))) count++;
            } catch (Exception e) {
                log.error("expire failed orderId={}", orderId, e);
            }
        }

        return count;
    }

    private boolean expireOne(UUID orderId) {
        int count = orderRepository.expire(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED, clock.instant());
        if (count == 0) return false;
        productApi.releaseReservation(orderId, ReleaseReason.EXPIRED);
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalStateException("주문을 찾을 수 없습니다. orderId=" + orderId)
        );
        order.cancelPendingChildren();
        publisher.closed(order, "EXPIRED");
        return true;
    }
}
