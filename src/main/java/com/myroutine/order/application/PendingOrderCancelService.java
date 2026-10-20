package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ReleaseReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingOrderCancelService {
    private final ProductApi productApi;
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public OrderStatus cancel(UUID memberId, UUID orderId) {
        orderRepository.findByIdAndMemberId(orderId, memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        );
        int count = orderRepository.cancelBeforePayment(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, clock.instant());
        if (count == 0) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        productApi.releaseReservation(orderId, ReleaseReason.PAYMENT_FAILED);
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalStateException("주문을 찾을 수 없습니다.")
        );
        order.cancelPendingChildren();
        return order.getStatus();
    }
}
