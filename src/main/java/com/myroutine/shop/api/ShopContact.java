package com.myroutine.shop.api;

import java.util.UUID;

public record ShopContact(
        UUID ownerId,
        String email
) {
}
