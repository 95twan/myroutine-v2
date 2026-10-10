package com.myroutine.product.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "product", schema = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Product extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ProductCategory category;

    @Column(name = "price", nullable = false)
    private Money price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "subscribable", nullable = false)
    private boolean subscribable;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    @OrderBy("sortOrder")
    private List<ProductImage> images = new ArrayList<>();

    public static final int MAX_IMAGES = 10;

    public static Product register(UUID shopId, String name, String description, ProductCategory category, Money price, boolean subscribable) {
        Product product = new Product();
        product.id = Ids.newId();
        product.shopId = shopId;
        product.name = name;
        product.description = description;
        product.category = category;
        product.price = price;
        product.status = ProductStatus.ON_SALE;
        product.subscribable = subscribable;
        return product;
    }

    public boolean isVisibleToPublic() {
        return status != ProductStatus.HIDDEN;
    }

    public Optional<PriceChange> update(String name, String description, ProductCategory category, Money price, Boolean subscribable) {
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_DISCONTINUED);
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (category != null) {
            this.category = category;
        }
        PriceChange priceChange = null;
        if (price != null && !this.price.equals(price)) {
            priceChange = new PriceChange(this.price, price);
            this.price = price;
        }
        if (subscribable != null) {
            this.subscribable = subscribable;
        }
        return Optional.ofNullable(priceChange);
    }

    public void changeStatus(ProductStatus to) {
        this.status = this.status.transitTo(to);
    }

    public ProductImage addImage(String objectKey) {
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_DISCONTINUED);
        }
        if (this.images.size() >= MAX_IMAGES) {
            throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        }

        int sortOrder;
        if (images.isEmpty()) {
            sortOrder = 0;
            this.thumbnailKey = objectKey;
        } else {
            sortOrder = images.getLast().getSortOrder() + 1;
        }
        ProductImage image = ProductImage.of(objectKey, sortOrder);
        this.images.add(image);
        return image;
    }

    public String removeImage(UUID imageId) {
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_DISCONTINUED);
        }
        ProductImage image = this.images.stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND));
        this.images.remove(image);

        this.thumbnailKey = this.images.isEmpty() ? null : this.images.getFirst().getObjectKey();

        return image.getObjectKey();
    }

    public static String objectKeyPrefix(UUID productId) {
        return "products/" + productId + "/";
    }
}
