package com.myroutine.order.application;

import com.myroutine.member.api.WithdrawalPrecondition;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.order.domain.RefundStatus;
import com.myroutine.order.domain.ShopOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderWithdrawalPrecondition implements WithdrawalPrecondition {

    private final OrderRepository orderRepository;
    private final Set<OrderStatus> activeOrderStatuses = Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS);
    private final Set<ShopOrderStatus> activeShopOrderStatuses = Set.of(ShopOrderStatus.PAID, ShopOrderStatus.SHIPPED, ShopOrderStatus.DELIVERED);
    private final Set<RefundStatus> activeRefundStatuses = Set.of(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PG_CANCELLED);

    @Override
    public List<String> violations(UUID memberId) {
        boolean active = orderRepository.existsActiveByMemberId(memberId, activeOrderStatuses, activeShopOrderStatuses, activeRefundStatuses);
        if (active) {
            return List.of("ACTIVE_ORDER");
        }
        return List.of();
    }
}
