package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.order.domain.*;
import com.myroutine.payment.api.PaymentApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRefundService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentApi paymentApi;
    private final TransactionTemplate tx;
    private final RefundProcessor refundProcessor;

    @Transactional(readOnly = true)
    public CursorPage<AdminRefundResult> getRefunds(RefundStatus status, String cursor, int size) {
        List<Refund> refunds;
        if (cursor == null || cursor.isBlank()) {
            refunds = refundRepository.findByStatusFirstPage(status, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            refunds = refundRepository.findByStatusNextPage(status, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (refunds.size() > size) {
            refunds = refunds.subList(0, size);
            Refund refund = refunds.getLast();
            nextCursor = new Cursor(refund.getCreatedAt(), refund.getId()).encode();
        }

        List<UUID> orderLineIds = refunds.stream()
                .map(Refund::getOrderLineId)
                .toList();

        Map<UUID, OrderLine> orderLineMap = orderRepository.findLinesByIdIn(orderLineIds).stream()
                .collect(Collectors.toMap(OrderLine::getId, Function.identity()));

        List<String> idempotencyKeys = refunds.stream()
                .map(Refund::pgIdempotencyKey)
                .toList();

        Map<String, String> cancelFailureCodeMap = paymentApi.findCancelFailureCodes(idempotencyKeys);

        List<AdminRefundResult> items = refunds.stream()
                .map(refund -> {
                    OrderLine orderLine = orderLineMap.get(refund.getOrderLineId());
                    String cancelFailureCode = cancelFailureCodeMap.get(refund.pgIdempotencyKey());
                    return AdminRefundResult.from(refund, orderLine, cancelFailureCode);
                })
                .toList();

        return new CursorPage<>(items, nextCursor);
    }

    public RefundResult retry(UUID refundId) {
        Refund refund = tx.execute(_ -> {
            Refund r = refundRepository.findById(refundId).orElseThrow(
                    () -> new BusinessException(OrderErrorCode.REFUND_NOT_FOUND)
            );
            r.retryPgCancel();
            return r;
        });
        RefundStatus status = refundProcessor.process(refundId);

        return switch (status) {
            case COMPLETED, APPROVED -> new RefundResult(refund.getId(), status, refund.getAmount().amount());
            case FAILED -> throw new BusinessException(CommonErrorCode.PG_ERROR);
            default ->
                    throw new IllegalStateException("예상하지 못한 환불 상태. refundId=" + refund.getId() + ", status=" + status);
        };
    }

    @Transactional
    public RefundResult close(UUID refundId, String reason) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.REFUND_NOT_FOUND)
        );
        refund.close(reason);

        if (refund.getReasonType() == RefundReasonType.RETURN) {
            Order order = orderRepository.findById(refund.getOrderId()).orElseThrow(
                    () -> new IllegalStateException("주문을 찾을 수 없습니다. orderId=" + refund.getOrderId())
            );
            order.rejectReturn(refund.getOrderLineId());
        }

        return new RefundResult(refund.getId(), refund.getStatus(), refund.getAmount().amount());
    }
}
