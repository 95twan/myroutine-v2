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

import static org.assertj.core.api.Assertions.*;

class OrderConfirmTest {

    @Test
    @DisplayName("한 품목을 확정하면 그 품목만 CONFIRMED가 되고 확정 시각이 기록되며 가게주문은 DELIVERED로 남는다.")
    void confirmLineConfirmsOnlyThatLine() {
        // Given
        UUID shopId = Ids.newId();
        Instant now = Instant.now();
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
        order.markPaid(now);

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);
        OrderLine line2 = shopOrder.getLines().get(1);

        order.ship(shopOrder.getId(), "CJ대한통운", "12345667890", now);
        order.deliver(shopOrder.getId(), now);

        // When
        order.confirmLine(line1.getId(), now);

        // Then
        assertThat(line1.getStatus()).isEqualTo(OrderLineStatus.CONFIRMED);
        assertThat(line1.getConfirmedAt()).isEqualTo(now);

        assertThat(line2.getStatus()).isEqualTo(OrderLineStatus.DELIVERED);
        assertThat(line2.getConfirmedAt()).isNull();

        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("반품 요청 중인 품목의 구매확정을 실패한다. (RETURN_REQUESTED)")
    void confirmReturnRequestedLineFails() {
        // Given
        UUID shopId = Ids.newId();
        Instant now = Instant.now();
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
        order.markPaid(now);

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);

        order.ship(shopOrder.getId(), "CJ대한통운", "12345667890", now);
        order.deliver(shopOrder.getId(), now);

        line1.markReturnRequested();

        // When & Then
        assertThatThrownBy(() -> order.confirmLine(line1.getId(), now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

        assertThat(line1.getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);
        assertThat(line1.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("마지막 품목을 확정하면 가게주문이 COMPLETED가 되고 완료 시각이 기록된다.")
    void confirmLastLineCompletesShopOrder() {
        // Given
        UUID shopId = Ids.newId();
        Instant now = Instant.now();
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
        order.markPaid(now);

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);
        OrderLine line2 = shopOrder.getLines().get(1);

        order.ship(shopOrder.getId(), "CJ대한통운", "12345667890", now);
        order.deliver(shopOrder.getId(), now);

        // When & Then
        order.confirmLine(line1.getId(), now);

        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.DELIVERED);

        order.confirmLine(line2.getId(), now);

        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("자동 확정은 반품 요청 중인 품목을 건너뛰고 나머지만 확정한다.")
    void autoConfirmSkipsReturnRequestedLine() {
        // Given
        UUID shopId = Ids.newId();
        Instant now = Instant.now();
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
        order.markPaid(now);

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);
        OrderLine line2 = shopOrder.getLines().get(1);

        order.ship(shopOrder.getId(), "CJ대한통운", "12345667890", now);
        order.deliver(shopOrder.getId(), now);

        line1.markReturnRequested();

        // When & Then
        int count = order.autoConfirm(shopOrder.getId(), now);

        assertThat(count).isEqualTo(1);
        assertThat(line1.getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);
        assertThat(line1.getConfirmedAt()).isNull();
        assertThat(line2.getStatus()).isEqualTo(OrderLineStatus.CONFIRMED);
        assertThat(line2.getConfirmedAt()).isNotNull();

        assertThat(shopOrder.getStatus()).isEqualTo(ShopOrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("확정 금액은 품목 금액에서 환불액을 뺀 값이다.")
    void confirmedAmountSubtractsRefundedAmount() {
        // Given
        UUID shopId = Ids.newId();
        Instant now = Instant.now();
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
        order.markPaid(now);

        ShopOrder shopOrder = order.getShopOrders().get(0);
        OrderLine line1 = shopOrder.getLines().get(0);
        OrderLine line2 = shopOrder.getLines().get(1);

        order.ship(shopOrder.getId(), "CJ대한통운", "12345667890", now);
        order.deliver(shopOrder.getId(), now);

        // When & Then
        line2.addRefundAmount(Money.of(500));
        assertThat(line2.confirmedAmount()).isEqualTo(Money.of(1500));
        assertThat(line1.confirmedAmount()).isEqualTo(Money.of(1000));
    }
}
