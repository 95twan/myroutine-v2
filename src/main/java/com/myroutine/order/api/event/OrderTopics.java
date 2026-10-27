package com.myroutine.order.api.event;

public class OrderTopics {
    public static final String ORDER_PAID = "order.order-paid.v1";
    public static final String ORDER_EXPIRED = "order.order-expired.v1";
    public static final String SHOP_ORDER_SHIPPED = "order.shop-order-shipped.v1";
    public static final String SHOP_ORDER_DELIVERED = "order.shop-order-delivered.v1";
    public static final String ORDER_LINE_REFUNDED = "order.order-line-refunded.v1";
    public static final String ORDER_LINE_CONFIRMED = "order.order-line-confirmed.v1";
}
