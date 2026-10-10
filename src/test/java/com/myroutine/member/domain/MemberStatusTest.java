package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberStatusTest {

    @ParameterizedTest(name = "[성공] {0} -> {1}")
    @DisplayName("회원 상태를 전이한다. (허용된 전이)")
    @MethodSource("successTransitProvider")
    void allowed_transitions(MemberStatus from, MemberStatus to) {
        // When
        MemberStatus result = from.transitTo(to);

        // Then
        assertThat(result).isEqualTo(to);
    }

    private static Stream<Arguments> successTransitProvider() {
        return Stream.of(
                Arguments.of(MemberStatus.ACTIVE, MemberStatus.BANNED),
                Arguments.of(MemberStatus.ACTIVE, MemberStatus.WITHDRAWN),
                Arguments.of(MemberStatus.BANNED, MemberStatus.ACTIVE)
        );
    }

    @ParameterizedTest(name = "[실패] {0} -> {1}")
    @DisplayName("회원 상태 전이를 실패한다. (허용되지 않은 전이)")
    @MethodSource("failTransitProvider")
    void forbidden_transitions(MemberStatus from, MemberStatus to) {
        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));
    }

    private static Stream<Arguments> failTransitProvider() {
        return Stream.of(
                Arguments.of(MemberStatus.ACTIVE, MemberStatus.ACTIVE),
                Arguments.of(MemberStatus.BANNED, MemberStatus.BANNED),
                Arguments.of(MemberStatus.BANNED, MemberStatus.WITHDRAWN),
                Arguments.of(MemberStatus.WITHDRAWN, MemberStatus.ACTIVE),
                Arguments.of(MemberStatus.WITHDRAWN, MemberStatus.BANNED),
                Arguments.of(MemberStatus.WITHDRAWN, MemberStatus.WITHDRAWN)
        );
    }
}
