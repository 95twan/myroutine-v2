package com.myroutine.product.domain;

import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "product_image", schema = "product")
@EntityListeners(AuditingEntityListener.class)
public class ProductImage {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ProductImage of(String objectKey, int sortOrder) {
        ProductImage productImage = new ProductImage();
        productImage.id = Ids.newId();
        productImage.objectKey = objectKey;
        productImage.sortOrder = sortOrder;
        return productImage;
    }
}
