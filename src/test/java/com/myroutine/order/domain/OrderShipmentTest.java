package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderShipmentTest {

    @Test
    @DisplayName("발송하면 가게주문이 SHIPPED가 되고 송장이 기록되며, PAID 품목은 SHIPPED가 되고 CANCELLED 품목은 그대로다.")
    void shipMarksShopOrderAndPaidLinesShipped() {
        // Given
        UUID shopId = Ids.newId();
        Order order = Order.checkout(
                Ids.newId(),
                List.of(
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product1",
                                null,
                                Money.of(1000),
                                1
                        ),
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product2",
                                null,
                                Money.of(2000),
                                2
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid(Instant.now());

        ShopOrder shopOrder = order.getShopOrders().get(0);
        shopOrder.getLines().get(0).markCancelled();
        UUID shopOrderId = shopOrder.getId();

        Instant shippedAt = Instant.now();

        // When
        order.ship(shopOrderId, "CJ대한통운", "1234567890", shippedAt);

        // Then
        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.SHIPPED);
        assertThat(shopOrder.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(shopOrder.getTrackingNumber()).isEqualTo("1234567890");
        assertThat(shopOrder.getShippedAt()).isEqualTo(shippedAt);
        assertThat(shopOrder.getLines().get(0).getStatus()).isEqualTo(OrderLineStatus.CANCELLED);
        assertThat(shopOrder.getLines().get(1).getStatus()).isEqualTo(OrderLineStatus.SHIPPED);
    }

    @Test
    @DisplayName("PAID가 아닌 가게주문의 발송을 실패한다. (INVALID_STATE_TRANSITION)")
    void shipFailsWhenShopOrderIsNotPaid() {
        // Given
        UUID shopId = Ids.newId();
        Order order = Order.checkout(
                Ids.newId(),
                List.of(
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product1",
                                null,
                                Money.of(1000),
                                1
                        ),
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product2",
                                null,
                                Money.of(2000),
                                2
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ShopOrder shopOrder = order.getShopOrders().get(0);
        UUID shopOrderId = shopOrder.getId();

        Instant shippedAt = Instant.now();

        // When & Then
        assertThatThrownBy(() -> order.ship(shopOrderId, "CJ대한통운", "1234567890", shippedAt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    @DisplayName("배송완료하면 가게주문과 SHIPPED 품목이 DELIVERED가 되고 배송완료 시각이 기록된다.")
    void deliverMarksShopOrderAndShippedLinesDelivered() {
        // Given
        UUID shopId = Ids.newId();
        Order order = Order.checkout(
                Ids.newId(),
                List.of(
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product1",
                                null,
                                Money.of(1000),
                                1
                        ),
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product2",
                                null,
                                Money.of(2000),
                                2
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid(Instant.now());

        ShopOrder shopOrder = order.getShopOrders().get(0);
        shopOrder.getLines().get(0).markCancelled();
        UUID shopOrderId = shopOrder.getId();

        Instant shippedAt = Instant.now();
        order.ship(shopOrderId, "CJ대한통운", "1234567890", shippedAt);

        Instant deliveredAt = Instant.now();

        // When
        order.deliver(shopOrderId, deliveredAt);

        // Then
        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.DELIVERED);
        assertThat(shopOrder.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(shopOrder.getLines().get(0).getStatus()).isEqualTo(OrderLineStatus.CANCELLED);
        assertThat(shopOrder.getLines().get(1).getStatus()).isEqualTo(OrderLineStatus.DELIVERED);
    }

    @Test
    @DisplayName("SHIPPED가 아닌 가게주문의 배송완료를 실패한다. (INVALID_STATE_TRANSITION)")
    void deliverFailsWhenShopOrderIsNotShipped() {
        // Given
        UUID shopId = Ids.newId();
        Order order = Order.checkout(
                Ids.newId(),
                List.of(
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product1",
                                null,
                                Money.of(1000),
                                1
                        ),
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product2",
                                null,
                                Money.of(2000),
                                2
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid(Instant.now());

        ShopOrder shopOrder = order.getShopOrders().get(0);
        shopOrder.getLines().get(0).markCancelled();
        UUID shopOrderId = shopOrder.getId();

        Instant deliveredAt = Instant.now();

        // When & Then
        assertThatThrownBy(() -> order.deliver(shopOrderId, deliveredAt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
    }
}
