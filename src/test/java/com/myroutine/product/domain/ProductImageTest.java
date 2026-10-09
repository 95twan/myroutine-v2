package com.myroutine.product.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageTest {

    @Test
    @DisplayName("첫 이미지를 추가하면 썸네일이 된다.")
    void addFirstImageBecomesThumbnail() {
        // Given
        Product product = Product.register(
                Ids.newId(),
                "product",
                "description",
                ProductCategory.FOOD,
                Money.of(10000),
                false
        );

        // When
        product.addImage("image1");

        // Then
        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getThumbnailKey()).isEqualTo("image1");
    }

    @Test
    @DisplayName("썸네일 이미지를 삭제하면 다음 이미지가 썸네일이 된다.")
    void removeThumbnailPromotesNextImage() {
        // Given
        Product product = Product.register(
                Ids.newId(),
                "product",
                "description",
                ProductCategory.FOOD,
                Money.of(10000),
                false
        );

        ProductImage image1 = product.addImage("image1");
        product.addImage("image2");

        // When
        product.removeImage(image1.getId());

        // Then
        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getThumbnailKey()).isEqualTo("image2");
    }

    @Test
    @DisplayName("모든 이미지를 삭제하면 썸네일이 null이 된다.")
    void removeAllImagesClearsThumbnail() {
        // Given
        Product product = Product.register(
                Ids.newId(),
                "product",
                "description",
                ProductCategory.FOOD,
                Money.of(10000),
                false
        );

        ProductImage image1 = product.addImage("image1");
        ProductImage image2 = product.addImage("image2");

        // When
        product.removeImage(image1.getId());
        product.removeImage(image2.getId());

        // Then
        assertThat(product.getImages()).hasSize(0);
        assertThat(product.getThumbnailKey()).isNull();
    }

    @Test
    @DisplayName("이미지 추가를 실패한다. (11번째 이미지)")
    void addEleventhImage() {
        // Given
        Product product = Product.register(
                Ids.newId(),
                "product",
                "description",
                ProductCategory.FOOD,
                Money.of(10000),
                false
        );
        for (int i = 1; i <= 10; i++) {
            product.addImage("image" + i);
        }

        // When & Then
        assertThatThrownBy(() -> product.addImage("image11"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("이미지 추가를 실패한다. (단종된 상품)")
    void addImageToDiscontinuedProduct() {
        // Given
        Product product = Product.register(
                Ids.newId(),
                "product",
                "description",
                ProductCategory.FOOD,
                Money.of(10000),
                false
        );

        product.changeStatus(ProductStatus.DISCONTINUED);

        // When & Then
        assertThatThrownBy(() -> product.addImage("image"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_DISCONTINUED));
    }

    @Test
    @DisplayName("이미지 삭제를 실패한다. (단종된 상품)")
    void removeImageFromDiscontinuedProduct() {
        // Given
        Product product = Product.register(
                Ids.newId(),
                "product",
                "description",
                ProductCategory.FOOD,
                Money.of(10000),
                false
        );
        ProductImage image = product.addImage("image");
        product.changeStatus(ProductStatus.DISCONTINUED);

        // When & Then
        assertThatThrownBy(() -> product.removeImage(image.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_DISCONTINUED));
    }
}
