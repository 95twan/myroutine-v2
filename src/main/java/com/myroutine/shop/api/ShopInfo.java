package com.myroutine.shop.api;

import com.myroutine.shop.domain.Shop;

import java.util.UUID;

public record ShopInfo(
        UUID shopId,
        UUID ownerId,
        String name
) {
    public static ShopInfo from(Shop shop) {
        return new ShopInfo(shop.getId(), shop.getMemberId(), shop.getName());
    }
}
