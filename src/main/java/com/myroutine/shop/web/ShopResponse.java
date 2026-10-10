package com.myroutine.shop.web;

import com.myroutine.shop.application.ShopResult;

import java.util.UUID;

public record ShopResponse(
        UUID id,
        String name,
        String phone,
        String address,
        String status
) {
    public static ShopResponse from(ShopResult result) {
        return new ShopResponse(
                result.id(),
                result.name(),
                result.phone(),
                result.address(),
                result.status().name()
        );
    }
}
