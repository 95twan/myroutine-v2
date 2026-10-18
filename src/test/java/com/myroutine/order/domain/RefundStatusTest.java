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

class RefundStatusTest {

    @ParameterizedTest
    @MethodSource("transitSuccessProvider")
    @DisplayName("환불 상태를 전이한다. (허용된 전이)")
    void transitSuccess(RefundStatus from, RefundStatus to) {
        // Given

        // When & Then
        assertThatCode(() -> from.transitTo(to))
                .doesNotThrowAnyException();

    }

    public static Stream<Arguments> transitSuccessProvider() {
        return Stream.of(
                Arguments.of(RefundStatus.REQUESTED, RefundStatus.APPROVED),
                Arguments.of(RefundStatus.REQUESTED, RefundStatus.REJECTED),
                Arguments.of(RefundStatus.APPROVED, RefundStatus.PG_CANCELLED),
                Arguments.of(RefundStatus.APPROVED, RefundStatus.FAILED),
                Arguments.of(RefundStatus.PG_CANCELLED, RefundStatus.COMPLETED)
        );
    }

    @ParameterizedTest
    @MethodSource("transitFailProvider")
    @DisplayName("환불 상태 전이를 실패한다. (허용되지 않은 전이)")
    void transitFail(RefundStatus from, RefundStatus to) {
        // Given

        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
    }

    public static Stream<Arguments> transitFailProvider() {
        Set<List<RefundStatus>> allowed = transitSuccessProvider()
                .map(a -> List.of((RefundStatus) a.get()[0], (RefundStatus) a.get()[1]))
                .collect(Collectors.toSet());

        return Arrays.stream(RefundStatus.values())
                .flatMap(s -> Arrays.stream(RefundStatus.values())
                        .filter(r -> !allowed.contains(List.of(s, r)))
                        .map(r -> Arguments.of(s, r))
                );
    }
}
