package com.myroutine.product.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    @DisplayName("가격을 변경하면 이전 가격과 새 가격이 담긴 PriceChange를 반환한다.")
    void updatePriceReturnsPriceChange() {
        // Given
        Product product = Product.register(
                UUID.randomUUID(),
                "product",
                "description",
                ProductCategory.ETC,
                Money.of(1000),
                false
        );

        // When
        Optional<PriceChange> result = product.update(
                null,
                null,
                null,
                Money.of(2000),
                null
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().oldPrice().amount()).isEqualTo(1000);
        assertThat(result.get().newPrice().amount()).isEqualTo(2000);

        assertThat(product.getName()).isEqualTo("product");
        assertThat(product.getDescription()).isEqualTo("description");
        assertThat(product.getCategory()).isEqualTo(ProductCategory.ETC);
        assertThat(product.isSubscribable()).isEqualTo(false);
    }

    @Test
    @DisplayName("이름만 변경하거나 같은 가격으로 수정하면 Optional.empty()를 반환한다.")
    void updateWithoutPriceChangeReturnsEmpty() {
        // Given
        Product product = Product.register(
                UUID.randomUUID(),
                "product",
                "description",
                ProductCategory.ETC,
                Money.of(1000),
                false
        );

        // When
        Optional<PriceChange> result = product.update(
                "updated product",
                null,
                null,
                Money.of(1000),
                null
        );

        // Then
        assertThat(result).isEmpty();
        assertThat(product.getName()).isEqualTo("updated product");
    }

    @Test
    @DisplayName("상품 수정을 실패한다. (단종된 상품)")
    void updateDiscontinuedProduct() {
        // Given
        Product product = Product.register(
                UUID.randomUUID(),
                "product",
                "description",
                ProductCategory.ETC,
                Money.of(1000),
                false
        );
        product.changeStatus(ProductStatus.DISCONTINUED);

        // When & Then
        assertThatThrownBy(() -> product.update(
                        "updated product",
                        "updated description",
                        null,
                        null,
                        null
                )
        ).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_DISCONTINUED)
        );

        assertThat(product.getName()).isEqualTo("product");
        assertThat(product.getDescription()).isEqualTo("description");
    }
}
