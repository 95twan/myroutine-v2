package com.myroutine.order.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "order_line", schema = "orders")
public class OrderLine extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "shop_order_id", insertable = false, updatable = false)
    private UUID shopOrderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "unit_price", nullable = false)
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_amount", nullable = false)
    private Money lineAmount;

    @Column(name = "refunded_amount", nullable = false)
    private Money refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderLineStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private Long version;

    private static final Set<OrderLineStatus> SETTLED =
            EnumSet.of(OrderLineStatus.CONFIRMED, OrderLineStatus.RETURNED, OrderLineStatus.CANCELLED);

    public static OrderLine create(CheckoutLine line) {
        OrderLine orderLine = new OrderLine();
        orderLine.id = Ids.newId();
        orderLine.productId = line.productId();
        orderLine.productName = line.productName();
        orderLine.thumbnailKey = line.thumbnailKey();
        orderLine.unitPrice = line.unitPrice();
        orderLine.quantity = line.quantity();
        orderLine.lineAmount = line.unitPrice().times(line.quantity());
        orderLine.refundedAmount = Money.ZERO;
        orderLine.status = OrderLineStatus.PENDING;
        orderLine.confirmedAt = null;
        return orderLine;
    }

    public void markPaid() {
        this.status = this.status.transitTo(OrderLineStatus.PAID);
    }

    public void markCancelled() {
        this.status = this.status.transitTo(OrderLineStatus.CANCELLED);
    }

    public void markShipped() {
        this.status = this.status.transitTo(OrderLineStatus.SHIPPED);
    }

    public void markDelivered() {
        this.status = this.status.transitTo(OrderLineStatus.DELIVERED);
    }

    public void addRefundAmount(Money amount) {
        this.refundedAmount = this.refundedAmount.plus(amount);
    }

    public void markReturned() {
        this.status = this.status.transitTo(OrderLineStatus.RETURNED);
    }

    public boolean isSettled() {
        return SETTLED.contains(status);
    }

    public Money refundableAmount() {
        return this.lineAmount.minus(this.refundedAmount);
    }

    public void markReturnRequested() {
        this.status = this.status.transitTo(OrderLineStatus.RETURN_REQUESTED);
    }
}
