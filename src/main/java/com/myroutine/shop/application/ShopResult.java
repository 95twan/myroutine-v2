package com.myroutine.shop.application;

import com.myroutine.shop.domain.Shop;
import com.myroutine.shop.domain.ShopStatus;

import java.time.Instant;
import java.util.UUID;

public record ShopResult(
        UUID id,
        UUID memberId,
        String name,
        String businessNumber,
        String email,
        String phone,
        String address,
        ShopStatus status,
        Instant createdAt
) {
    public static ShopResult from(Shop shop) {
        return new ShopResult(
                shop.getId(),
                shop.getMemberId(),
                shop.getName(),
                shop.getBusinessNumber(),
                shop.getEmail(),
                shop.getPhone(),
                shop.getAddress(),
                shop.getStatus(),
                shop.getCreatedAt()
        );
    }
}
