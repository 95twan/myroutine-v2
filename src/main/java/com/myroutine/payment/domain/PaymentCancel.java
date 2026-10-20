package com.myroutine.payment.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "payment_cancel", schema = "payment")
public class PaymentCancel extends BaseTimeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentCancelStatus status;

    @Column(name = "failure_code")
    private String failureCode;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
