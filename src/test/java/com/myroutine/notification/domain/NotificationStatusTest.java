package com.myroutine.notification.domain;

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

class NotificationStatusTest {

    @ParameterizedTest(name = "[성공] {0} -> {1}")
    @DisplayName("알림 상태를 전이한다. (허용된 전이)")
    @MethodSource("successTransitProvider")
    void transitSuccess(NotificationStatus from, NotificationStatus to) {
        // When
        NotificationStatus result = from.transitTo(to);

        // Then
        assertThat(result).isEqualTo(to);
    }

    private static Stream<Arguments> successTransitProvider() {
        return Stream.of(
                Arguments.of(NotificationStatus.PENDING, NotificationStatus.SENT),
                Arguments.of(NotificationStatus.PENDING, NotificationStatus.FAILED),
                Arguments.of(NotificationStatus.FAILED, NotificationStatus.PENDING)
        );
    }

    @ParameterizedTest(name = "[실패] {0} -> {1}")
    @DisplayName("알림 상태 전이를 실패한다. (허용되지 않은 전이)")
    @MethodSource("failTransitProvider")
    void transitFail(NotificationStatus from, NotificationStatus to) {
        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
    }

    private static Stream<Arguments> failTransitProvider() {
        Set<List<Object>> allowed = successTransitProvider()
                .map(arguments -> List.of(arguments.get()))
                .collect(Collectors.toSet());

        return Arrays.stream(NotificationStatus.values())
                .flatMap(from -> Arrays.stream(NotificationStatus.values()).map(to -> Arguments.of(from, to)))
                .filter(arguments -> !allowed.contains(List.of(arguments.get())));
    }
}
