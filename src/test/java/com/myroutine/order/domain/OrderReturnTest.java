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

class OrderReturnTest {

    @Test
    @DisplayName("반품 요청 중이 아닌 품목의 반품 거절을 실패한다. (INVALID_STATE_TRANSITION)")
    void rejectReturnOfNonReturnRequestedLineFails() {
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

        order.ship(shopOrder.getId(), "CJ", "12345667890", Instant.now());

        // When & Then
        assertThatThrownBy(() -> order.rejectReturn(line1.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

        assertThat(line1.getStatus()).isEqualTo(OrderLineStatus.SHIPPED);
    }
}
