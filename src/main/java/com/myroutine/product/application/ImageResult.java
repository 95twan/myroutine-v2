package com.myroutine.product.application;

import com.myroutine.product.domain.ProductImage;

import java.util.UUID;

public record ImageResult(
        UUID imageId,
        String objectKey,
        int sortOrder
) {
    public static ImageResult from(ProductImage productImage) {
        return new ImageResult(
                productImage.getId(),
                productImage.getObjectKey(),
                productImage.getSortOrder()
        );
    }
}
