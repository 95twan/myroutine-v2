package com.myroutine.order.application;

import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.order.api.event.*;
import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderLine;
import com.myroutine.order.domain.Refund;
import com.myroutine.order.domain.ShopOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final OutboxPublisher outboxPublisher;

    public void paid(Order order) {
        UUID orderId = order.getId();

        OrderPaidEvent event = new OrderPaidEvent(
                orderId,
                order.getMemberId(),
                order.getTotalAmount().amount(),
                order.getShopOrders().stream()
                        .map(shopOrder -> new PaidShopOrder(
                                shopOrder.getId(),
                                shopOrder.getShopId(),
                                shopOrder.getLines().stream()
                                        .map(line -> new PaidLine(
                                                line.getId(),
                                                line.getProductId(),
                                                line.getProductName(),
                                                line.getQuantity()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
        outboxPublisher.publish(OrderTopics.ORDER_PAID, "ORDER", orderId, event);
    }

    public void closed(Order order, String reason) {
        UUID orderId = order.getId();

        OrderExpiredEvent event = new OrderExpiredEvent(
                orderId,
                order.getMemberId(),
                reason
        );
        outboxPublisher.publish(OrderTopics.ORDER_EXPIRED, "ORDER", orderId, event);
    }

    public void shipped(Order order, ShopOrder shopOrder) {
        UUID orderId = order.getId();

        ShopOrderShippedEvent event = new ShopOrderShippedEvent(
                shopOrder.getId(),
                orderId,
                order.getMemberId(),
                shopOrder.getCarrier(),
                shopOrder.getTrackingNumber()
        );
        outboxPublisher.publish(OrderTopics.SHOP_ORDER_SHIPPED, "ORDER", orderId, event);
    }

    public void delivered(Order order, ShopOrder shopOrder) {
        UUID orderId = order.getId();

        ShopOrderDeliveredEvent event = new ShopOrderDeliveredEvent(
                shopOrder.getId(),
                orderId,
                order.getMemberId()
        );
        outboxPublisher.publish(OrderTopics.SHOP_ORDER_DELIVERED, "ORDER", orderId, event);
    }

    public void lineRefunded(Refund refund, Order order, OrderLine orderLine) {
        UUID orderId = order.getId();

        OrderLineRefundedEvent event = new OrderLineRefundedEvent(
                refund.getId(),
                orderLine.getId(),
                orderId,
                order.getMemberId(),
                refund.getAmount().amount()
        );
        outboxPublisher.publish(OrderTopics.ORDER_LINE_REFUNDED, "ORDER", orderId, event);
    }

    public void lineConfirmed(Order order, ShopOrder shopOrder, OrderLine orderLine) {
        UUID orderId = order.getId();

        OrderLineConfirmedEvent event = new OrderLineConfirmedEvent(
                orderLine.getId(),
                orderId,
                shopOrder.getShopId(),
                orderLine.getProductId(),
                order.getMemberId(),
                orderLine.confirmedAmount().amount(),
                orderLine.getConfirmedAt()
        );
        outboxPublisher.publish(OrderTopics.ORDER_LINE_CONFIRMED, "ORDER", orderId, event);
    }
}
