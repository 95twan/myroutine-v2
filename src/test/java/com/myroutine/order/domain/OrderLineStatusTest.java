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

class OrderLineStatusTest {

    @ParameterizedTest
    @MethodSource("transitSuccessProvider")
    @DisplayName("주문 품목 상태를 전이한다. (허용된 전이)")
    void transitSuccess(OrderLineStatus from, OrderLineStatus to) {
        // Given


        // When & Then
        assertThatCode(() -> from.transitTo(to)).doesNotThrowAnyException();
    }

    public static Stream<Arguments> transitSuccessProvider() {
        return Stream.of(
                Arguments.of(OrderLineStatus.PENDING, OrderLineStatus.PAID),
                Arguments.of(OrderLineStatus.PENDING, OrderLineStatus.CANCELLED),
                Arguments.of(OrderLineStatus.PAID, OrderLineStatus.SHIPPED),
                Arguments.of(OrderLineStatus.PAID, OrderLineStatus.CANCELLED),
                Arguments.of(OrderLineStatus.SHIPPED, OrderLineStatus.DELIVERED),
                Arguments.of(OrderLineStatus.DELIVERED, OrderLineStatus.CONFIRMED),
                Arguments.of(OrderLineStatus.DELIVERED, OrderLineStatus.RETURN_REQUESTED),
                Arguments.of(OrderLineStatus.RETURN_REQUESTED, OrderLineStatus.DELIVERED),
                Arguments.of(OrderLineStatus.RETURN_REQUESTED, OrderLineStatus.RETURNED)
        );
    }

    @ParameterizedTest
    @MethodSource("transitFailProvider")
    @DisplayName("주문 품목 상태 전이를 실패한다. (허용되지 않은 전이)")
    void transitFail(OrderLineStatus from, OrderLineStatus to) {
        // Given

        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

    }

    public static Stream<Arguments> transitFailProvider() {
        Set<List<OrderLineStatus>> allowed = transitSuccessProvider()
                .map(a -> List.of((OrderLineStatus) a.get()[0], (OrderLineStatus) a.get()[1]))
                .collect(Collectors.toSet());

        return Arrays.stream(OrderLineStatus.values())
                .flatMap(from -> Arrays.stream(OrderLineStatus.values()).map(to -> List.of(from, to)))
                .filter(pair -> !allowed.contains(pair))
                .map(pair -> Arguments.of(pair.get(0), pair.get(1)));
    }

}
