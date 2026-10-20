package com.myroutine.order.application;

import com.myroutine.common.model.Money;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.payment.api.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReconcileService {
    private final OrderRepository orderRepository;
    private final PaymentApi paymentApi;
    private final OrderPaymentApplier orderPaymentApplier;
    private final TransactionTemplate tx;
    private final Clock clock;

    public int reconcileBatch() {
        Instant now = clock.instant();
        List<UUID> ids = orderRepository.findStuckIds(OrderStatus.PAYMENT_IN_PROGRESS, now.minusSeconds(30), Limit.of(100));

        int count = 0;

        for (UUID orderId : ids) {
            try {
                if (reconcileOne(orderId)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("결제 정보 처리 중 오류가 발생했습니다. orderId: {}", orderId, e);
            }
        }
        return count;
    }

    private boolean reconcileOne(UUID orderId) {
        Optional<PaymentView> view = paymentApi.find(orderId);
        if (view.isEmpty()) {
            log.error("결제 정보가 없습니다. orderId: {}", orderId);
            return false;
        }
        PaymentState state = view.get().state();

        switch (state) {
            case APPROVED, FAILED -> {
                return closeConfirmed(orderId, view.get());
            }
            case IN_PROGRESS, UNKNOWN -> {
                return closeByQuery(orderId, view.get());
            }
            case CANCELLED -> {
                tx.executeWithoutResult(s -> orderPaymentApplier.failOrder(orderId, "결제 완료 처리 실패로 자동 취소"));
                return true;
            }
            case PARTIAL_CANCELLED -> {
                log.error("대사 대상 주문의 결제가 부분 취소 상태입니다. 보상은 전액 취소라 나올 수 없는 상태입니다. orderId: {}", orderId);
                return false;
            }
        }

        return false;
    }

    private boolean closeConfirmed(UUID orderId, PaymentView view) {
        return closeOrder(orderId, view.state(), view.failureCode(), view.approvedAt(), view.amount());
    }

    private boolean closeByQuery(UUID orderId, PaymentView view) {
        PgOutcome outcome = paymentApi.queryPgOutcome(orderId);
        if (outcome.type() == PgOutcomeType.UNKNOWN) {
            return false;
        }
        PaymentState state = tx.execute(s -> paymentApi.applyOutcome(view.paymentId(), outcome));
        return closeOrder(orderId, state, outcome.code(), outcome.approvedAt(), view.amount());
    }

    private boolean closeOrder(UUID orderId, PaymentState state, String failureCode, Instant approvedAt, long amount) {
        Instant now = clock.instant();
        try {
            OrderStatus status = tx.execute(s -> orderPaymentApplier.applyPaymentState(orderId, state, failureCode));
            if (status == OrderStatus.PAID || status == OrderStatus.PAYMENT_FAILED) {
                return true;
            }
        } catch (Exception e) {
            if (state == PaymentState.APPROVED && approvedAt.isBefore(now.minus(Duration.ofMinutes(5)))) {
                log.warn("주문 완료 처리에 실패해 보상 취소를 시작합니다. orderId: {}", orderId, e);
                CancelOutcome cancelled = paymentApi.cancel(orderId, Money.of(amount), "주문 완료 실패로 자동 취소", "order-compensate-" + orderId);
                if (cancelled == CancelOutcome.DONE) {
                    tx.executeWithoutResult(s -> orderPaymentApplier.failOrder(orderId, "결제 완료 처리 실패로 자동 취소"));
                    return true;
                } else {
                    log.error("주문 완료 실패 후 보상 취소가 완료되지 않았습니다. 다음 주기에 다시 시도합니다. orderId: {}, cancelOutcome: {}", orderId, cancelled, e);
                }
            } else {
                throw e;
            }
        }
        return false;
    }
}
