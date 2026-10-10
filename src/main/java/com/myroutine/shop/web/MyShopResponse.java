package com.myroutine.shop.web;

import com.myroutine.shop.application.ShopResult;
import com.myroutine.shop.domain.ShopStatus;

import java.time.Instant;
import java.util.UUID;

public record MyShopResponse(
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
    public static MyShopResponse from(ShopResult result) {
        return new MyShopResponse(
                result.id(),
                result.memberId(),
                result.name(),
                result.businessNumber(),
                result.email(),
                result.phone(),
                result.address(),
                result.status(),
                result.createdAt()
        );
    }
}
