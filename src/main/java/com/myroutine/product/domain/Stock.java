package com.myroutine.product.domain;

import com.myroutine.common.model.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "stock", schema = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Stock extends BaseTimeEntity {
    @Id
    private UUID productId;

    @Column(name = "available", nullable = false)
    private int available;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "sold", nullable = false)
    private int sold;

    @Column(name = "received", nullable = false)
    private int received;

    public boolean inStock() {
        return available > 0;
    }
}
