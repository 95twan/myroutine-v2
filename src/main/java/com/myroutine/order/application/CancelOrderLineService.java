package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Money;
import com.myroutine.order.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelOrderLineService {
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final RefundProcessor refundProcessor;
    private final TransactionTemplate tx;

    public RefundResult cancel(UUID memberId, UUID orderId, UUID lineId, String reason) {
        Refund refund = tx.execute(s -> {
            Order order = orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId).orElseThrow(
                    () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
            );
            order.verifyCancellable(lineId);
            ShopOrder shopOrder = order.shopOrderOf(lineId);
            OrderLine line = order.findLine(lineId);
            Money amount = line.refundableAmount();
            return refundRepository.save(Refund.approveBuyerCancel(orderId, shopOrder.getId(), line.getId(), memberId, amount, reason));
        });
        RefundStatus status = refundProcessor.process(refund.getId());

        return switch (status) {
            case COMPLETED, APPROVED -> new RefundResult(refund.getId(), status, refund.getAmount().amount());
            case FAILED -> throw new BusinessException(CommonErrorCode.PG_ERROR);
            default -> throw new IllegalStateException("예상하지 못한 환불 상태. refundId=" + refund.getId() + ", status=" + status);
        };
    }
}
