package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.payment.api.PaymentApi;
import com.myroutine.payment.api.PgOutcome;
import com.myroutine.payment.api.PgOutcomeType;
import com.myroutine.payment.api.StartPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfirmPaymentService {

    private final PaymentApi paymentApi;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;
    private final OrderPaymentApplier orderPaymentApplier;
    private final Clock clock;

    public ConfirmResult confirm(UUID memberId, UUID orderId, ConfirmPaymentCommand command) {
        Started started = transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdAndMemberId(orderId, memberId).orElseThrow(
                    () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
            );
            if (order.getTotalAmount().amount() != command.amount() || !order.pgOrderId().equals(command.pgOrderId())) {
                throw new BusinessException(CommonErrorCode.AMOUNT_MISMATCH);
            }
            Instant now = clock.instant();
            int count = orderRepository.startPayment(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS, now);
            if (count == 0) {
                Order o = orderRepository.findById(orderId).orElseThrow(
                        () -> new IllegalStateException("주문을 찾을 수 없습니다.")
                );
                return switch (o.getStatus()) {
                    case PAID -> {
                        throw new BusinessException(OrderErrorCode.ORDER_ALREADY_PAID);
                    }
                    case PAYMENT_IN_PROGRESS ->
                            new Started(null, new ConfirmResult(orderId, OrderStatus.PAYMENT_IN_PROGRESS));
                    case PENDING_PAYMENT -> {
                        if (!o.getExpiresAt().isAfter(now)) {
                            throw new BusinessException(OrderErrorCode.ORDER_EXPIRED);
                        }
                        throw new IllegalStateException("" + orderId);
                    }
                    case CANCELLED, EXPIRED, PAYMENT_FAILED -> {
                        throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
                    }
                };
            } else {
                UUID paymentId = paymentApi.begin(new StartPayment(orderId, memberId, order.getTotalAmount(), order.pgOrderId(), command.paymentKey()));
                return new Started(paymentId, null);
            }
        });

        if (started.early() != null) {
            return started.early();
        }
        PgOutcome outcome = paymentApi.callConfirm(started.paymentId());

        OrderStatus status = transactionTemplate.execute(s -> orderPaymentApplier.apply(orderId, started.paymentId(), outcome));

        if (status == OrderStatus.PAID) {
            return new ConfirmResult(orderId, OrderStatus.PAID);
        } else if (status == OrderStatus.PAYMENT_FAILED) {
            if (outcome.type() == PgOutcomeType.REJECTED) {
                throw new BusinessException(CommonErrorCode.PAYMENT_FAILED, Map.of("pgCode", outcome.code()));
            }
            throw new BusinessException(CommonErrorCode.PAYMENT_FAILED);
        } else {
            return new ConfirmResult(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
        }
    }
}
