package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.order.domain.OrderLine;
import com.myroutine.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfirmPurchaseService {
    private final OrderRepository orderRepository;
    private final Clock clock;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public OrderLineResult confirm(UUID memberId, UUID orderId, UUID lineId) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        );
        order.confirmLine(lineId, clock.instant());
        OrderLine line = order.findLine(lineId);
        eventPublisher.lineConfirmed(order, order.shopOrderOf(line.getId()), line);

        return new OrderLineResult(line.getId(), line.getStatus(), line.getConfirmedAt());
    }
}
