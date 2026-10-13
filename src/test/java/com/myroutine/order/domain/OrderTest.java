package com.myroutine.order.domain;

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

class OrderTest {

    @Test
    @DisplayName("가게 2곳·품목 3개로 체크아웃하면 가게주문이 2개 만들어지고 총액은 품목 금액(단가 × 수량)의 합이다.")
    void checkoutGroupsLinesByShop() {
        // Given
        UUID memberId = Ids.newId();
        UUID shopId1 = Ids.newId();
        UUID shopId2 = Ids.newId();
        List<CheckoutLine> lines = List.of(
                new CheckoutLine(Ids.newId(), shopId1, "shop1", "product1", null, Money.of(1000), 2),
                new CheckoutLine(Ids.newId(), shopId1, "shop1", "product2", null, Money.of(2000), 3),
                new CheckoutLine(Ids.newId(), shopId2, "shop2", "product3", null, Money.of(3000), 4)
        );

        // When
        Order order = Order.checkout(
                memberId,
                lines,
                new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        // Then
        assertThat(order.getShopOrders()).hasSize(2);
        assertThat(order.getTotalAmount().amount()).isEqualTo(1000 * 2 + 2000 * 3 + 3000 * 4);
        assertThat(order.getShopOrders())
                .filteredOn(shopOrder -> shopOrder.getShopId().equals(shopId1))
                .singleElement()
                .satisfies(so -> assertThat(so.getLines()).hasSize(2));
        assertThat(order.getShopOrders())
                .filteredOn(shopOrder -> shopOrder.getShopId().equals(shopId2))
                .singleElement()
                .satisfies(so -> assertThat(so.getLines()).hasSize(1));
    }

    @Test
    @DisplayName("체크아웃하면 만료 시각은 현재 시각 + 결제 유효시간(15분)이다.")
    void checkoutSetsExpiresAt() {
        // Given
        UUID memberId = Ids.newId();
        UUID shopId = Ids.newId();
        List<CheckoutLine> lines = List.of(
                new CheckoutLine(Ids.newId(), shopId, "shop", "product", null, Money.of(1000), 2)
        );

        Instant now = Instant.now();

        // When
        Order order = Order.checkout(
                memberId,
                lines,
                new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2"),
                now,
                Duration.ofMinutes(15)
        );

        // Then
        assertThat(order.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("결제 완료 처리하면 주문·가게주문·품목이 모두 PAID가 된다.")
    void markPaid() {
        // Given
        UUID memberId = Ids.newId();
        UUID shopId1 = Ids.newId();
        UUID shopId2 = Ids.newId();
        List<CheckoutLine> lines = List.of(
                new CheckoutLine(Ids.newId(), shopId1, "shop1", "product1", null, Money.of(1000), 2),
                new CheckoutLine(Ids.newId(), shopId2, "shop2", "product3", null, Money.of(3000), 4)
        );

        Order order = Order.checkout(
                memberId,
                lines,
                new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);

        Instant now = Instant.now();
        // When
        order.markPaid(now);

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(now);
        assertThat(order.getShopOrders()).extracting(ShopOrder::getStatus).containsOnly(ShopOrderStatus.PAID);
        assertThat(order.getShopOrders()).flatMap(ShopOrder::getLines).extracting(OrderLine::getStatus).containsOnly(OrderLineStatus.PAID);
    }

    @Test
    @DisplayName("결제 실패 처리하면 주문은 PAYMENT_FAILED, 가게주문·품목은 CANCELLED가 되고 사유가 저장된다.")
    void markPaymentFailed() {
        // Given
        UUID memberId = Ids.newId();
        UUID shopId1 = Ids.newId();
        UUID shopId2 = Ids.newId();
        List<CheckoutLine> lines = List.of(
                new CheckoutLine(Ids.newId(), shopId1, "shop1", "product1", null, Money.of(1000), 2),
                new CheckoutLine(Ids.newId(), shopId2, "shop2", "product3", null, Money.of(3000), 4)
        );

        Order order = Order.checkout(
                memberId,
                lines,
                new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);

        // When
        order.markPaymentFailed("PG가 거절");

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getFailureReason()).isEqualTo("PG가 거절");
        assertThat(order.getShopOrders()).extracting(ShopOrder::getStatus).containsOnly(ShopOrderStatus.CANCELLED);
        assertThat(order.getShopOrders()).flatMap(ShopOrder::getLines).extracting(OrderLine::getStatus).containsOnly(OrderLineStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제 실패 사유가 200자를 넘으면 200자로 잘라 저장한다.")
    void markPaymentFailedTruncatesReason() {
        // Given
        UUID memberId = Ids.newId();
        UUID shopId1 = Ids.newId();
        UUID shopId2 = Ids.newId();
        List<CheckoutLine> lines = List.of(
                new CheckoutLine(Ids.newId(), shopId1, "shop1", "product1", null, Money.of(1000), 2),
                new CheckoutLine(Ids.newId(), shopId2, "shop2", "product3", null, Money.of(3000), 4)
        );

        Order order = Order.checkout(
                memberId,
                lines,
                new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);

        // When
        order.markPaymentFailed("a".repeat(200) + "b");

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getFailureReason().length()).isEqualTo(200);
        assertThat(order.getFailureReason()).isEqualTo("a".repeat(200));
        assertThat(order.getShopOrders()).extracting(ShopOrder::getStatus).containsOnly(ShopOrderStatus.CANCELLED);
        assertThat(order.getShopOrders()).flatMap(ShopOrder::getLines).extracting(OrderLine::getStatus).containsOnly(OrderLineStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제 완료된 주문에 대기 중인 하위 주문 취소를 호출해도 PAID 가게주문·품목은 바뀌지 않는다.")
    void cancelPendingChildrenKeepsPaid() {
        // Given
        UUID memberId = Ids.newId();
        UUID shopId1 = Ids.newId();
        UUID shopId2 = Ids.newId();
        List<CheckoutLine> lines = List.of(
                new CheckoutLine(Ids.newId(), shopId1, "shop1", "product1", null, Money.of(1000), 2),
                new CheckoutLine(Ids.newId(), shopId2, "shop2", "product3", null, Money.of(3000), 4)
        );

        Order order = Order.checkout(
                memberId,
                lines,
                new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2"),
                Instant.now(),
                Duration.ofMinutes(15)
        );

        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS);

        Instant now = Instant.now();

        order.markPaid(now);

        // When
        order.cancelPendingChildren();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(now);
        assertThat(order.getShopOrders()).extracting(ShopOrder::getStatus).containsOnly(ShopOrderStatus.PAID);
        assertThat(order.getShopOrders()).flatMap(ShopOrder::getLines).extracting(OrderLine::getStatus).containsOnly(OrderLineStatus.PAID);
    }

}
