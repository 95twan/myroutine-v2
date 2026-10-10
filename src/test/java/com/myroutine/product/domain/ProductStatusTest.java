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
    @DisplayName("상품 상태를 전이한다. (허용된 전이)")
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
    @DisplayName("상품 상태 전이를 실패한다. (허용되지 않은 전이)")
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
