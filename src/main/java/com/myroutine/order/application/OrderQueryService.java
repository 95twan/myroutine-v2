package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.order.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    @Transactional(readOnly = true)
    public CursorPage<OrderSummaryResult> getMyOrders(UUID memberId, String cursor, int size) {
        List<Order> orders;
        if (cursor == null || cursor.isEmpty()) {
            orders = orderRepository.findByMemberFirstPage(memberId, Limit.of(size + 1));
        } else {
            Cursor currentCursor = Cursor.decode(cursor);
            orders = orderRepository.findByMemberNextPage(memberId, currentCursor.createdAt(), currentCursor.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (orders.size() > size) {
            Order next = orders.get(size - 1);
            nextCursor = new Cursor(next.getCreatedAt(), next.getId()).encode();
            orders = orders.subList(0, size);
        }

        return new CursorPage<>(orders.stream().map(OrderSummaryResult::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public OrderDetailResult getMyOrder(UUID memberId, UUID orderId) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        );
        List<Refund> refunds = refundRepository.findAllByOrderId(orderId);
        return OrderDetailResult.from(order, refunds);
    }
}
