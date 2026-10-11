package com.myroutine.order.domain;

import com.myroutine.common.model.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cart_item", schema = "orders")
public class CartItem extends BaseTimeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void changeQuantity(int quantity) {
        if (quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException("수량은 1부터 99 사이의 값이어야 합니다.");
        }
        this.quantity = quantity;
    }
}
