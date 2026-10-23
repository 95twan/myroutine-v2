package com.myroutine.order.application;

import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.payment.api.PaymentApi;
import com.myroutine.payment.api.PaymentState;
import com.myroutine.payment.api.PgOutcome;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ReleaseReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class OrderPaymentApplier {

    private final OrderRepository orderRepository;
    private final ProductApi productApi;
    private final PaymentApi paymentApi;
    private final CartService cartService;
    private final Clock clock;
    private final OrderEventPublisher publisher;

    @Transactional
    public OrderStatus apply(UUID orderId, UUID paymentId, PgOutcome outcome) {
        PaymentState state = paymentApi.applyOutcome(paymentId, outcome);

        return applyPaymentState(orderId, state, outcome.code());
    }

    @Transactional
    public OrderStatus applyPaymentState(UUID orderId, PaymentState state, String failureCode) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalStateException("주문 내역이 없습니다.")
        );

        if (order.getStatus() != OrderStatus.PAYMENT_IN_PROGRESS) {
            return order.getStatus();
        }

        switch (state) {
            case APPROVED -> complete(order);
            case FAILED -> fail(order, "PG 거절: " + failureCode);
        }

        return order.getStatus();
    }

    @Transactional
    public void failOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalStateException("주문 내역이 없습니다.")
        );
        fail(order, reason);
    }

    private void complete(Order order) {
        productApi.commitReservation(order.getId());
        order.markPaid(clock.instant());
        publisher.paid(order);
        cartService.deductOrdered(order.getMemberId(), order.quantitiesByProduct());
    }

    private void fail(Order order, String reason) {
        productApi.releaseReservation(order.getId(), ReleaseReason.PAYMENT_FAILED);
        order.markPaymentFailed(reason);
        publisher.closed(order, "PAYMENT_FAILED");
    }
}
