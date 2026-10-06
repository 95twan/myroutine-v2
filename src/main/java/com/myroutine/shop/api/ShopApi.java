package com.myroutine.shop.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShopApi {
    void verifyOwnerOfActiveShop(UUID shopId, UUID memberId);
    void verifyOwner(UUID shopId, UUID memberId);
    List<ShopInfo> requireActiveShops(Collection<UUID> shopIds);
}
