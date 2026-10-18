package com.myroutine.order.application;

import com.myroutine.order.domain.Refund;
import com.myroutine.order.domain.RefundRepository;
import com.myroutine.order.domain.RefundStatus;
import com.myroutine.payment.api.CancelOutcome;
import com.myroutine.payment.api.PaymentApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundProcessor {
    private final RefundRepository refundRepository;
    private final PaymentApi paymentApi;
    private final RefundCompleter refundCompleter;
    private final TransactionTemplate tx;

    public RefundStatus process(UUID refundId) {
        Refund refund = getRefund(refundId);
        if (refund.needsPgCancel()) {
            CancelOutcome outcome = paymentApi.cancel(refund.getOrderId(), refund.getAmount(), "품목 취소", refund.pgIdempotencyKey());
            switch (outcome) {
                case DONE -> tx.executeWithoutResult(s -> {
                    Refund r = getRefund(refundId);
                    r.markPgCancelled();
                });
                case FAILED -> {
                    tx.executeWithoutResult(s -> {
                        Refund r = getRefund(refundId);
                        r.fail();
                    });
                    return RefundStatus.FAILED;
                }
                case UNKNOWN -> {
                    return RefundStatus.APPROVED;
                }
            }
        }
        return refundCompleter.complete(refundId);
    }

    private Refund getRefund(UUID refundId) {
        return refundRepository.findById(refundId).orElseThrow(
                () -> new IllegalStateException("환불을 찾을 수 없습니다. refundId=" + refundId)
        );
    }

}
