package com.myroutine.shop.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.shop.domain.Shop;
import com.myroutine.shop.domain.ShopErrorCode;
import com.myroutine.shop.domain.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopQueryService {
    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public List<ShopResult> getMyShops(UUID memberId) {
        List<Shop> shops = shopRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);

        return shops.stream()
                .map(ShopResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShopResult getShop(UUID shopId) {
        Shop shop = shopRepository.findById(shopId).orElseThrow(
                () -> new BusinessException(ShopErrorCode.SHOP_NOT_FOUND)
        );

        return ShopResult.from(shop);
    }
}
