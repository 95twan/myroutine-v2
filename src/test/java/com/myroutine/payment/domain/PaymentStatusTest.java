package com.myroutine.payment.domain;

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

class PaymentStatusTest {

    @ParameterizedTest
    @MethodSource("transitSuccessProvider")
    @DisplayName("결제 상태를 전이한다. (허용된 전이)")
    void transitSuccess(PaymentStatus from, PaymentStatus to) {
        // Given


        // When & Then
        assertThatCode(() -> from.transitTo(to)).doesNotThrowAnyException();
    }

    public static Stream<Arguments> transitSuccessProvider() {
        return Stream.of(
                Arguments.of(PaymentStatus.IN_PROGRESS, PaymentStatus.APPROVED),
                Arguments.of(PaymentStatus.IN_PROGRESS, PaymentStatus.FAILED),
                Arguments.of(PaymentStatus.IN_PROGRESS, PaymentStatus.UNKNOWN),
                Arguments.of(PaymentStatus.UNKNOWN, PaymentStatus.APPROVED),
                Arguments.of(PaymentStatus.UNKNOWN, PaymentStatus.FAILED),
                Arguments.of(PaymentStatus.APPROVED, PaymentStatus.PARTIAL_CANCELLED),
                Arguments.of(PaymentStatus.APPROVED, PaymentStatus.CANCELLED),
                Arguments.of(PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.PARTIAL_CANCELLED),
                Arguments.of(PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.CANCELLED)
        );
    }

    @ParameterizedTest
    @MethodSource("transitFailProvider")
    @DisplayName("결제 상태 전이를 실패한다. (허용되지 않은 전이)")
    void transitFail(PaymentStatus from, PaymentStatus to) {
        // Given


        // When & Then
        assertThatThrownBy(() -> from.transitTo(to)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

    }

    public static Stream<Arguments> transitFailProvider() {
        Set<List<PaymentStatus>> allowed = transitSuccessProvider()
                .map(a -> List.of((PaymentStatus) a.get()[0], (PaymentStatus) a.get()[1]))
                .collect(Collectors.toSet());

        return Arrays.stream(PaymentStatus.values())
                .flatMap(from -> Arrays.stream(PaymentStatus.values())
                        .filter(to -> !allowed.contains(List.of(from, to)))
                        .map(to -> Arguments.of(from, to)));
    }

}
