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

class OrderRefundTest {

    @Test
    @DisplayName("환불을 반영하면 품목의 환불액이 증가하고 품목이 CANCELLED가 된다.")
    void applyRefundIncreasesRefundedAmountAndCancelsLine() {
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
                                1
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid(Instant.now());

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);
        OrderLine line2 = shopOrder.getLines().get(1);

        // When
        order.applyRefund(line1.getId(), line1.refundableAmount(), RefundReasonType.BUYER_CANCEL, Instant.now());

        // Then
         assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.PAID);

        assertThat(line1.getRefundedAmount()).isEqualTo(Money.of(1000));
        assertThat(line1.getStatus()).isEqualTo(OrderLineStatus.CANCELLED);

        assertThat(line2.getRefundedAmount()).isEqualTo(Money.of(0));
        assertThat(line2.getStatus()).isEqualTo(OrderLineStatus.PAID);
    }

    @Test
    @DisplayName("마지막 품목을 취소하면 가게주문이 CANCELLED가 된다.")
    void cancelLastLineCancelsShopOrder() {
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
                                1
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid(Instant.now());

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);
        OrderLine line2 = shopOrder.getLines().get(1);

        // When & Then
        order.applyRefund(line1.getId(), line1.refundableAmount(), RefundReasonType.BUYER_CANCEL, Instant.now());
        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.PAID);

        order.applyRefund(line2.getId(), line2.refundableAmount(), RefundReasonType.BUYER_CANCEL, Instant.now());
        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("발송된 가게주문의 품목 취소 가능 여부 검증을 실패한다. (발송됨)")
    void verifyCancellableFailsWhenShipped() {
        // Given
        UUID shopId = Ids.newId();
        Order order = Order.checkout(
                Ids.newId(),
                List.of(
                        new CheckoutLine(
                                Ids.newId(),
                                shopId,
                                "shop",
                                "product",
                                null,
                                Money.of(1000),
                                1
                        )
                ),
                new ShippingAddress("recipient", "010-1111-1111", "28403", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid(Instant.now());

        ShopOrder shopOrder = order.getShopOrders().get(0);

        order.ship(shopOrder.getId(), "CJ 대한통운", "1234567890", Instant.now());

        OrderLine line = shopOrder.getLines().get(0);

        // When & Then
        assertThatThrownBy(() -> order.verifyCancellable(line.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.SHIPPED);
        assertThat(line.getStatus()).isEqualTo(OrderLineStatus.SHIPPED);
    }
}
