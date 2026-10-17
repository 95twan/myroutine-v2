package com.myroutine.order.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "shop_order", schema = "orders")
public class ShopOrder extends BaseTimeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShopOrderStatus status;

    @Column(name = "carrier")
    private String carrier;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "shop_order_id", nullable = false, updatable = false)
    private List<OrderLine> lines = new ArrayList<>();

    public static ShopOrder create(UUID shopId, String shopName, List<CheckoutLine> lines) {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.id = Ids.newId();
        shopOrder.shopId = shopId;
        shopOrder.shopName = shopName;
        shopOrder.status = ShopOrderStatus.PENDING;
        shopOrder.lines.addAll(lines.stream().map(OrderLine::create).toList());
        return shopOrder;
    }

    public void markPaid() {
        this.status = this.status.transitTo(ShopOrderStatus.PAID);
        this.lines.forEach(OrderLine::markPaid);
    }

    public void markCancelled() {
        this.status = this.status.transitTo(ShopOrderStatus.CANCELLED);
        this.lines.forEach(OrderLine::markCancelled);
    }

    public void ship(String carrier, String trackingNumber, Instant now) {
        this.status = this.status.transitTo(ShopOrderStatus.SHIPPED);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = now;
        this.lines.stream()
                .filter(line -> line.getStatus() != OrderLineStatus.CANCELLED)
                .forEach(OrderLine::markShipped);
    }

    public void deliver(Instant now) {
        this.status = this.status.transitTo(ShopOrderStatus.DELIVERED);
        this.deliveredAt = now;
        this.lines.stream()
                .filter(line -> line.getStatus() == OrderLineStatus.SHIPPED)
                .forEach(OrderLine::markDelivered);
    }
}
