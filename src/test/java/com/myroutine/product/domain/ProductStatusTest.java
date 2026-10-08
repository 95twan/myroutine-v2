package com.myroutine.product.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class ProductStatusTest {

    @ParameterizedTest
    @MethodSource("transitSuccessProvider")
    @DisplayName("전 상태에서 다음 상태로 전이할 수 있는 경우 다음 상태로 전이된다.")
    void transitSuccess(ProductStatus from, ProductStatus to) {
        // Given

        // When & Then
        assertThatCode(() -> from.transitTo(to)).doesNotThrowAnyException();

    }

    public static Stream<Arguments> transitSuccessProvider() {
        return Stream.of(
                Arguments.of(ProductStatus.ON_SALE, ProductStatus.HIDDEN),
                Arguments.of(ProductStatus.ON_SALE, ProductStatus.DISCONTINUED),
                Arguments.of(ProductStatus.HIDDEN, ProductStatus.ON_SALE),
                Arguments.of(ProductStatus.HIDDEN, ProductStatus.DISCONTINUED)
        );
    }

    @ParameterizedTest
    @MethodSource("transitFailProvider")
    @DisplayName("이전 상태에서 다음 상태로 전이할 수 없는 경우 예외가 발생한다.")
    void transitFail(ProductStatus from, ProductStatus to) {
        // Given

        // When & Then
        assertThatThrownBy(() -> from.transitTo(to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION)
                );
    }

    public static Stream<Arguments> transitFailProvider() {
        return Stream.of(
                Arguments.of(ProductStatus.ON_SALE, ProductStatus.ON_SALE),
                Arguments.of(ProductStatus.HIDDEN, ProductStatus.HIDDEN),
                Arguments.of(ProductStatus.DISCONTINUED, ProductStatus.ON_SALE),
                Arguments.of(ProductStatus.DISCONTINUED, ProductStatus.HIDDEN),
                Arguments.of(ProductStatus.DISCONTINUED, ProductStatus.DISCONTINUED)
        );
    }

}
