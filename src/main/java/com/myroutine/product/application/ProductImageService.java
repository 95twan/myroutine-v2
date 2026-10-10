package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Ids;
import com.myroutine.common.storage.ObjectStorage;
import com.myroutine.common.storage.PresignedUpload;
import com.myroutine.common.storage.StorageProperties;
import com.myroutine.product.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Pattern OBJECT_KEY_PATTERN =
            Pattern.compile("^products/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|png|webp)$");

    private final ShopApi shopApi;
    private final ProductRepository productRepository;
    private final ObjectStorage objectStorage;
    private final StorageProperties properties;
    private final TransactionTemplate transactionTemplate;

    public UploadUrlResult issueUploadUrl(UUID memberId, UUID shopId, UUID productId, String contentType, long contentLength) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);
        if (!EXTENSIONS.containsKey(contentType) || contentLength < 1 || contentLength > properties.maxUploadBytes()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        String key = transactionTemplate.execute(status -> {
                    Product product = productRepository.findByIdAndShopId(productId, shopId).orElseThrow(
                            () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
                    );

                    if (product.getStatus() == ProductStatus.DISCONTINUED) {
                        throw new BusinessException(ProductErrorCode.PRODUCT_DISCONTINUED);
                    }

                    if (product.getImages().size() >= Product.MAX_IMAGES) {
                        throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
                    }
                    return Product.objectKeyPrefix(product.getId()) + Ids.newId() + "." + EXTENSIONS.get(contentType);
                }
        );

        PresignedUpload presigned = objectStorage.presignPut(key, contentType, contentLength);

        return new UploadUrlResult(presigned.url(), presigned.headers(), key, presigned.expiresAt());
    }

    public ImageResult register(UUID memberId, UUID shopId, UUID productId, String objectKey) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);
        if (!objectKey.startsWith(Product.objectKeyPrefix(productId)) || !OBJECT_KEY_PATTERN.matcher(objectKey).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (!objectStorage.exists(objectKey)) {
            throw new BusinessException(ProductErrorCode.IMAGE_NOT_UPLOADED);
        }

        return transactionTemplate.execute(status -> {
            Product product = productRepository.findByIdAndShopId(productId, shopId).orElseThrow(
                    () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
            );

            ProductImage image = product.addImage(objectKey);

            return new ImageResult(image.getId(), image.getObjectKey(), image.getSortOrder());
        });
    }

    public void delete(UUID memberId, UUID shopId, UUID productId, UUID imageId) {
        shopApi.verifyOwnerOfActiveShop(shopId, memberId);
        String key = transactionTemplate.execute(status -> {
            Product product = productRepository.findByIdAndShopId(productId, shopId).orElseThrow(
                    () -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
            );
            return product.removeImage(imageId);
        });
        try {
            objectStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("저장소의 이미지 삭제에 실패했습니다. key: {}, cause: {}", key, e.getMessage());
        }
    }
}
