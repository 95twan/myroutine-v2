package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class OrderStatusTest {

    @ParameterizedTest
    @MethodSource("transitSuccessProvider")
    @DisplayName("주문 상태를 전이한다. (허용된 전이)")
    void transitSuccess(OrderStatus from, OrderStatus to) {
        // Given

        // When & Then
        assertThatCode(() -> from.transitTo(to)).doesNotThrowAnyException();

    }

    public static Stream<Arguments> transitSuccessProvider() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS),
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED),
                Arguments.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PAID),
                Arguments.of(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PAYMENT_FAILED)
        );
    }

    @ParameterizedTest
    @MethodSource("transitFailProvider")
    @DisplayName("주문 상태 전이를 실패한다. (허용되지 않은 전이)")
    void transitFail(OrderStatus from, OrderStatus to) {
        // Given

        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

    }

    public static Stream<Arguments> transitFailProvider() {
        Set<List<OrderStatus>> allowed = transitSuccessProvider()
                .map(a -> List.of((OrderStatus) a.get()[0], (OrderStatus) a.get()[1]))
                .collect(Collectors.toSet());

        return Arrays.stream(OrderStatus.values())
                .flatMap(from -> Arrays.stream(OrderStatus.values()).map(to -> List.of(from, to)))
                .filter(pair -> !allowed.contains(pair))
                .map(pair -> Arguments.of(pair.get(0), pair.get(1)));
    }

}
