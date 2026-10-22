package com.myroutine.shop.api;

import java.util.UUID;

public interface ShopClosePrecondition {
    void check(UUID shopId);
}
