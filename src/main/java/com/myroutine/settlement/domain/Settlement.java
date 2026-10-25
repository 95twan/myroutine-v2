package com.myroutine.settlement.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement", schema = "settlement")
@Getter
public class Settlement extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "gross_amount", nullable = false)
    private Money grossAmount;

    @Column(name = "fee_amount", nullable = false)
    private Money feeAmount;

    @Column(name = "net_amount", nullable = false)
    private Money netAmount;

    @Column(name = "fee_rate_bp", nullable = false)
    private int feeRateBp;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    @Column(name = "payout_attempts", nullable = false)
    private int payoutAttempts;

    @Column(name = "payout_reference")
    private String payoutReference;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void calculate(Money gross, int bp, int itemCount) {
        this.grossAmount = gross;
        this.feeRateBp = bp;
        this.itemCount = itemCount;
        this.feeAmount = Money.of(gross.amount() * bp / 10000);
        this.netAmount = gross.minus(this.feeAmount);
    }

    public void markPaid(String payoutReference, Instant now) {
        this.status = this.status.transitTo(SettlementStatus.PAID);
        this.payoutReference = payoutReference;
        this.paidAt = now;
    }

    public void markPayoutFailed(String error) {
        this.status = this.status.transitTo(SettlementStatus.PAYOUT_FAILED);
        this.lastError = error;
        this.payoutAttempts++;
    }
}
