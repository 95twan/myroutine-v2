package com.myroutine.order.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund", schema = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Refund extends BaseTimeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "shop_order_id", nullable = false)
    private UUID shopOrderId;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false)
    private RefundReasonType reasonType;

    @Column(name = "reason")
    private String reason;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "pg_attempts", nullable = false)
    private int pgAttempts;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Refund approveBuyerCancel(UUID orderId, UUID shopOrderId, UUID orderLineId, UUID memberId, Money amount, String reason) {
        Refund refund = new Refund();
        refund.id = Ids.newId();
        refund.orderId = orderId;
        refund.shopOrderId = shopOrderId;
        refund.orderLineId = orderLineId;
        refund.memberId = memberId;
        refund.reasonType = RefundReasonType.BUYER_CANCEL;
        refund.reason = reason;
        refund.amount = amount;
        refund.status = RefundStatus.APPROVED;
        refund.pgAttempts = 0;
        return refund;
    }

    public static Refund requestReturn(UUID orderId, UUID shopOrderId, UUID orderLineId, UUID memberId, Money amount, String reason) {
        Refund refund = new Refund();
        refund.id = Ids.newId();
        refund.orderId = orderId;
        refund.shopOrderId = shopOrderId;
        refund.orderLineId = orderLineId;
        refund.memberId = memberId;
        refund.reasonType = RefundReasonType.RETURN;
        refund.reason = reason;
        refund.amount = amount;
        refund.status = RefundStatus.REQUESTED;
        refund.pgAttempts = 0;
        return refund;
    }

    public void markPgCancelled() {
        this.status = this.status.transitTo(RefundStatus.PG_CANCELLED);
    }

    public void complete(Instant now) {
        this.status = this.status.transitTo(RefundStatus.COMPLETED);
        this.completedAt = now;
    }

    public void fail() {
        this.status = this.status.transitTo(RefundStatus.FAILED);
    }

    public boolean needsPgCancel() {
        return this.status == RefundStatus.APPROVED;
    }

    public String pgIdempotencyKey() {
        if (this.pgAttempts == 0) {
            return this.id.toString();
        }
        return this.id + "-r" + this.pgAttempts;
    }

    public boolean readyToComplete() {
        return this.status == RefundStatus.PG_CANCELLED;
    }

    public void approve() {
        this.status = this.status.transitTo(RefundStatus.APPROVED);
    }

    public void reject(String reason) {
        this.status = this.status.transitTo(RefundStatus.REJECTED);
        this.rejectReason = reason;
    }
}
