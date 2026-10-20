package com.myroutine.order.application;

import com.myroutine.order.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerShopOrderResult(
        UUID shopOrderId,
        UUID orderId,
        ShopOrderStatus status,
        ShippingAddressResult shippingAddress,
        String carrier,
        String trackingNumber,
        Instant shippedAt,
        Instant deliveredAt,
        long amount,
        List<SellerLineResult> lines,
        List<SellerRefundResult> refunds,
        Instant createdAt
) {
    public static SellerShopOrderResult from(Order order, ShopOrder shopOrder, List<Refund> refunds) {
        return new SellerShopOrderResult(
                shopOrder.getId(),
                order.getId(),
                shopOrder.getStatus(),
                ShippingAddressResult.from(order.getShippingAddress()),
                shopOrder.getCarrier(),
                shopOrder.getTrackingNumber(),
                shopOrder.getShippedAt(),
                shopOrder.getDeliveredAt(),
                shopOrder.getLines().stream()
                        .filter(line -> line.getStatus() != OrderLineStatus.CANCELLED)
                        .mapToLong(line -> line.getLineAmount().amount())
                        .sum(),
                shopOrder.getLines().stream()
                        .map(SellerLineResult::from)
                        .toList(),
                refunds.stream()
                        .map(SellerRefundResult::from)
                        .toList(),
                shopOrder.getCreatedAt()
        );
    }

    public static SellerShopOrderResult from(ShopOrderListRow row, List<OrderLine> lines, List<Refund> refunds) {
        return new SellerShopOrderResult(
                row.shopOrderId(),
                row.orderId(),
                row.status(),
                ShippingAddressResult.from(row.shippingAddress()),
                row.carrier(),
                row.trackingNumber(),
                row.shippedAt(),
                row.deliveredAt(),
                lines.stream()
                        .filter(line -> line.getStatus() != OrderLineStatus.CANCELLED)
                        .mapToLong(line -> line.getLineAmount().amount())
                        .sum(),
                lines.stream()
                        .map(SellerLineResult::from)
                        .toList(),
                refunds.stream()
                        .map(SellerRefundResult::from)
                        .toList(),
                row.createdAt()
        );
    }

}
