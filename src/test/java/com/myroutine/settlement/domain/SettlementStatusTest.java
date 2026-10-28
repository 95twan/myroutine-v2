package com.myroutine.settlement.domain;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementStatusTest {

    @ParameterizedTest(name = "[성공] {0} -> {1}")
    @DisplayName("정산 상태를 전이한다. (허용된 전이)")
    @MethodSource("successTransitProvider")
    void allowedTransitions(SettlementStatus from, SettlementStatus to) {
        // When
        SettlementStatus result = from.transitTo(to);

        // Then
        assertThat(result).isEqualTo(to);
    }

    private static Stream<Arguments> successTransitProvider() {
        return Stream.of(
                Arguments.of(SettlementStatus.CALCULATED, SettlementStatus.PAID),
                Arguments.of(SettlementStatus.CALCULATED, SettlementStatus.PAYOUT_FAILED),
                Arguments.of(SettlementStatus.PAYOUT_FAILED, SettlementStatus.PAID),
                Arguments.of(SettlementStatus.PAYOUT_FAILED, SettlementStatus.PAYOUT_FAILED)
        );
    }

    @ParameterizedTest(name = "[실패] {0} -> {1}")
    @DisplayName("정산 상태 전이를 실패한다. (허용되지 않은 전이)")
    @MethodSource("failTransitProvider")
    void forbiddenTransitions(SettlementStatus from, SettlementStatus to) {
        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
    }

    private static Stream<Arguments> failTransitProvider() {
        Set<List<Object>> allowed = successTransitProvider()
                .map(arguments -> List.of(arguments.get()))
                .collect(Collectors.toSet());

        return Arrays.stream(SettlementStatus.values())
                .flatMap(from -> Arrays.stream(SettlementStatus.values()).map(to -> Arguments.of(from, to)))
                .filter(arguments -> !allowed.contains(List.of(arguments.get())));
    }
}
