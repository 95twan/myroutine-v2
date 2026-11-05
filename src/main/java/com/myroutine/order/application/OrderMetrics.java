package com.myroutine.order.application;

import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.order.domain.RefundRepository;
import com.myroutine.order.domain.RefundStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderMetrics {

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;
    private final AtomicLong sagaStuckOrderPayment = new AtomicLong();
    private final AtomicLong sagaStuckRefund = new AtomicLong();

    public OrderMetrics(MeterRegistry registry, OrderRepository orderRepository, RefundRepository refundRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.clock = clock;
        Gauge.builder("saga_stuck_count", sagaStuckOrderPayment, AtomicLong::doubleValue)
                .tag("type", "order_payment")
                .register(registry);
        Gauge.builder("saga_stuck_count", sagaStuckRefund, AtomicLong::doubleValue)
                .tag("type", "refund")
                .register(registry);
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        sagaStuckOrderPayment.set(orderRepository.countByStatusAndUpdatedAtBefore(OrderStatus.PAYMENT_IN_PROGRESS, clock.instant().minus(Duration.ofMinutes(5))));
        sagaStuckRefund.set(refundRepository.countByStatusInAndUpdatedAtBefore(List.of(RefundStatus.APPROVED, RefundStatus.PG_CANCELLED), clock.instant().minus(Duration.ofMinutes(10))));
    }
}
