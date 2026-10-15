package com.myroutine.payment.domain;

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
@Table(name = "payment", schema = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Column(name = "cancelled_amount", nullable = false)
    private Money cancelledAmount;

    @Column(name = "pg_order_id", nullable = false)
    private String pgOrderId;

    @Column(name = "payment_key", nullable = false)
    private String paymentKey;

    @Column(name = "billing_key_id")
    private UUID billingKeyId;

    @Column(name = "method")
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "reconcile_attempts", nullable = false)
    private int reconcileAttempts;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Payment begin(UUID orderId, UUID memberId, Money amount, String pgOrderId, String paymentKey) {
        Payment payment = new Payment();
        payment.id = Ids.newId();
        payment.orderId = orderId;
        payment.memberId = memberId;
        payment.amount = amount;
        payment.cancelledAmount = Money.ZERO;
        payment.pgOrderId = pgOrderId;
        payment.paymentKey = paymentKey;
        payment.status = PaymentStatus.IN_PROGRESS;
        payment.reconcileAttempts = 0;
        return payment;
    }
}
