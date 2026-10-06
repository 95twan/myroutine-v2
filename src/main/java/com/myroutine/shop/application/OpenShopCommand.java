package com.myroutine.shop.application;

public record OpenShopCommand(
        String name,
        String businessNumber,
        String email,
        String phone,
        String address
) {
}
