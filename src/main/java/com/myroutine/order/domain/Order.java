package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "orders", schema = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Order extends BaseTimeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "type", nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Money totalAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
    private ShippingAddress shippingAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("createdAt")
    private List<ShopOrder> shopOrders = new ArrayList<>();

    public static Order checkout(UUID memberId, List<CheckoutLine> lines, ShippingAddress address, Instant now, Duration paymentTtl) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("주문 품목이 비어 있습니다.");
        }
        Map<UUID, List<CheckoutLine>> linesByShop = lines.stream()
                .collect(Collectors.groupingBy(CheckoutLine::shopId, LinkedHashMap::new, Collectors.toList()));

        Order order = new Order();

        linesByShop.forEach((shopId, shopLines) -> {
            order.shopOrders.add(ShopOrder.create(shopId, shopLines.get(0).shopName(), shopLines));
        });

        order.id = Ids.newId();
        order.memberId = memberId;
        order.type = "NORMAL";
        order.status = OrderStatus.PENDING_PAYMENT;
        order.totalAmount = order.shopOrders.stream()
                .flatMap(shopOrder -> shopOrder.getLines().stream())
                .map(OrderLine::getLineAmount)
                .reduce(Money.ZERO, Money::plus);
        order.shippingAddress = address;
        order.expiresAt = now.plus(paymentTtl);

        return order;
    }

    public String pgOrderId() {
        return "ORD-" + id;
    }

    public void markPaid(Instant now) {
        this.status = this.status.transitTo(OrderStatus.PAID);
        this.shopOrders.forEach(ShopOrder::markPaid);
        this.paidAt = now;
    }

    public void markPaymentFailed(String reason) {
        this.status = this.status.transitTo(OrderStatus.PAYMENT_FAILED);
        this.failureReason = reason.substring(0, Math.min(reason.length(), 200));
        cancelPendingChildren();
    }

    public void cancelPendingChildren() {
        this.shopOrders.stream()
                .filter(shopOrder -> shopOrder.getStatus() == ShopOrderStatus.PENDING)
                .forEach(ShopOrder::markCancelled);
    }

    public Map<UUID, Integer> quantitiesByProduct() {
        return this.shopOrders.stream()
                .flatMap(shopOrder -> shopOrder.getLines().stream())
                .collect(Collectors.groupingBy(OrderLine::getProductId, Collectors.summingInt(OrderLine::getQuantity)));
    }

    public ShopOrder findShopOrder(UUID shopOrderId) {
        return this.shopOrders.stream()
                .filter(shopOrder -> shopOrder.getId().equals(shopOrderId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND));
    }

    public void ship(UUID shopOrderId, String carrier, String trackingNumber, Instant now) {
        findShopOrder(shopOrderId).ship(carrier, trackingNumber, now);
    }

    public void deliver(UUID shopOrderId, Instant now) {
        findShopOrder(shopOrderId).deliver(now);
    }

    public OrderLine findLine(UUID lineId) {
        return this.shopOrders.stream()
                .flatMap(shopOrder -> shopOrder.getLines().stream())
                .filter(line -> line.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_LINE_NOT_FOUND));
    }

    public ShopOrder shopOrderOf(UUID lineId) {
        return this.shopOrders.stream()
                .filter(shopOrder -> shopOrder.getLines().stream()
                        .anyMatch(line -> line.getId().equals(lineId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND));
    }

    public void verifyCancellable(UUID lineId) {
        OrderLine line = findLine(lineId);

        if (line.getStatus() != OrderLineStatus.PAID) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    public void applyRefund(UUID lineId, Money amount, RefundReasonType type, Instant now) {
        OrderLine line = findLine(lineId);
        ShopOrder shopOrder = shopOrderOf(lineId);

        line.addRefundAmount(amount);

        switch (type) {
            case BUYER_CANCEL -> line.markCancelled();
            case RETURN -> line.markReturned();
        }

        recalculateShopOrder(shopOrder, now);
    }

    private void recalculateShopOrder(ShopOrder shopOrder, Instant now) {
        if (shopOrder.allLinesCancelled()) {
            shopOrder.markCancelledWithoutLines();
            return;
        }
        if (shopOrder.getStatus() == ShopOrderStatus.DELIVERED && shopOrder.allLinesSettled()) {
            shopOrder.markCompleted(now);
        }
    }

    public void requestReturn(UUID lineId) {
        OrderLine line = findLine(lineId);
        line.markReturnRequested();
    }

    public void rejectReturn(UUID lineId) {
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.RETURN_REQUESTED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        line.markDelivered();
    }
}
