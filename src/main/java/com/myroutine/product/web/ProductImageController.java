package com.myroutine.product.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.storage.ImageUrls;
import com.myroutine.product.application.ImageResult;
import com.myroutine.product.application.ProductImageService;
import com.myroutine.product.application.UploadUrlResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {
    private final ProductImageService productImageService;
    private final ImageUrls imageUrls;

    @PostMapping("/presigned-url")
    public ResponseEntity<UploadUrlResponse> issueUploadUrl(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @RequestBody @Valid UploadUrlRequest request
    ) {
        UploadUrlResult result = productImageService.issueUploadUrl(memberId, shopId, productId, request.contentType(), request.contentLength());
        return ResponseEntity.ok(UploadUrlResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<ImageResponse> register(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @RequestBody @Valid RegisterImageRequest request
    ) {
        ImageResult result = productImageService.register(memberId, shopId, productId, request.objectKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(ImageResponse.from(result, imageUrls));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId
    ) {
        productImageService.delete(memberId, shopId, productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
