package com.myroutine.product.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.product.application.ImageResult;

import java.util.UUID;

public record ImageResponse(
        UUID imageId,
        String url,
        int sortOrder
) {
    public static ImageResponse from(ImageResult result, ImageUrls imageUrls) {
        return new ImageResponse(result.imageId(), imageUrls.toUrl(result.objectKey()), result.sortOrder());
    }
}
