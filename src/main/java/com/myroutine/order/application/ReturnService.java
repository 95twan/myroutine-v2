package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.order.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReturnService {
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final RefundProcessor refundProcessor;
    private final ShopApi shopApi;
    private final TransactionTemplate tx;

    @Transactional
    public RefundResult request(UUID memberId, UUID orderId, UUID lineId, String reason) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        );
        OrderLine orderLine = order.findLine(lineId);
        if (refundRepository.existsByOrderLineIdAndStatus(lineId, RefundStatus.REJECTED)) {
            throw new BusinessException(OrderErrorCode.RETURN_NOT_ALLOWED);
        }
        order.requestReturn(lineId);
        ShopOrder shopOrder = order.shopOrderOf(lineId);
        Refund refund = refundRepository.save(
                Refund.requestReturn(
                        orderId,
                        shopOrder.getId(),
                        lineId,
                        memberId,
                        orderLine.refundableAmount(),
                        reason
                )
        );

        return new RefundResult(refund.getId(), refund.getStatus(), refund.getAmount().amount());
    }

    public RefundResult approve(UUID memberId, UUID shopId, UUID refundId) {
        Refund refund = tx.execute(s -> {
            shopApi.verifyOwner(shopId, memberId);
            Refund r = refundRepository.findById(refundId).orElseThrow(
                    () -> new BusinessException(OrderErrorCode.REFUND_NOT_FOUND)
            );
            Order order = orderRepository.findById(r.getOrderId()).orElseThrow(
                    () -> new IllegalStateException("주문을 찾을 수 없습니다. orderId=" + r.getOrderId())
            );
            ShopOrder shopOrder = order.findShopOrder(r.getShopOrderId());
            if (!shopOrder.getShopId().equals(shopId)) {
                throw new BusinessException(OrderErrorCode.REFUND_NOT_FOUND);
            }
            r.approve();
            return r;
        });

        RefundStatus status = refundProcessor.process(refund.getId());

        return switch (status) {
            case COMPLETED, APPROVED -> new RefundResult(refund.getId(), status, refund.getAmount().amount());
            case FAILED -> throw new BusinessException(CommonErrorCode.PG_ERROR);
            default -> throw new IllegalStateException("예상하지 못한 환불 상태. refundId=" + refund.getId() + ", status=" + status);
        };
    }

    @Transactional
    public RefundResult reject(UUID memberId, UUID shopId, UUID refundId, String reason) {
        shopApi.verifyOwner(shopId, memberId);
        Refund refund = refundRepository.findById(refundId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.REFUND_NOT_FOUND)
        );
        Order order = orderRepository.findById(refund.getOrderId()).orElseThrow(
                () -> new IllegalStateException("주문을 찾을 수 없습니다. orderId=" + refund.getOrderId())
        );
        ShopOrder shopOrder = order.findShopOrder(refund.getShopOrderId());
        if (!shopOrder.getShopId().equals(shopId)) {
            throw new BusinessException(OrderErrorCode.REFUND_NOT_FOUND);
        }
        refund.reject(reason);
        order.rejectReturn(refund.getOrderLineId());
        return new RefundResult(refund.getId(), refund.getStatus(), refund.getAmount().amount());
    }
}
