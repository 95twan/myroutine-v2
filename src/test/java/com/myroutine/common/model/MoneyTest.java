package com.myroutine.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("금액 생성을 실패한다. (음수 금액)")
    void createWithNegativeAmount() {
        // Given


        // When & Then
        assertThatThrownBy(() -> Money.of(-1000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액 빼기를 실패한다. (결과가 음수)")
    void minusToNegative() {
        // Given
        Money money = Money.of(1000);

        // When & Then
        assertThatThrownBy(() -> money.minus(Money.of(2000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액 곱하기를 실패한다. (오버플로)")
    void timesOverflow() {
        // Given
        Money money = Money.of(Long.MAX_VALUE);

        // When & Then
        assertThatThrownBy(() -> money.times(2)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("더하기와 곱하기가 정상 동작한다.")
    void plusAndTimes() {
        // Given
        Money money = Money.of(1000);

        // When & Then
        assertThat(money.plus(Money.of(2000))).isEqualTo(Money.of(3000));
        assertThat(money.times(2)).isEqualTo(Money.of(2000));
    }
}
