package com.myroutine.shop.application;

public record UpdateShopCommand(
        String name,
        String email,
        String phone,
        String address
) {
}
