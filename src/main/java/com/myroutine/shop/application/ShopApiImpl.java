package com.myroutine.shop.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.shop.api.ShopApi;
import com.myroutine.shop.api.ShopInfo;
import com.myroutine.shop.domain.Shop;
import com.myroutine.shop.domain.ShopErrorCode;
import com.myroutine.shop.domain.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ShopApiImpl implements ShopApi {

    private final ShopRepository shopRepository;

    @Override
    @Transactional(readOnly = true)
    public void verifyOwnerOfActiveShop(UUID shopId, UUID memberId) {
        Shop shop = shopRepository.findById(shopId).orElseThrow(
                () -> new BusinessException(ShopErrorCode.SHOP_NOT_FOUND)
        );
        shop.verifyOwner(memberId);
        shop.verifyActive();
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyOwner(UUID shopId, UUID memberId) {
        Shop shop = shopRepository.findById(shopId).orElseThrow(
                () -> new BusinessException(ShopErrorCode.SHOP_NOT_FOUND)
        );
        shop.verifyOwner(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShopInfo> requireActiveShops(Collection<UUID> shopIds) {
        Set<UUID> requested = new LinkedHashSet<>(shopIds);   // 중복 제거
        if (requested.isEmpty()) {
            return List.of();
        }

        List<Shop> shops = shopRepository.findAllByIdIn(requested);
        Set<UUID> activeIds = shops.stream()
                .filter(Shop::isActive)
                .map(Shop::getId)
                .collect(Collectors.toSet());

        // 조회 안 된 ID + CLOSED인 ID를 한 번에 모음
        List<UUID> invalidIds = requested.stream()
                .filter(id -> !activeIds.contains(id))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new BusinessException(ShopErrorCode.SHOP_NOT_ACTIVE, Map.of("shopIds", invalidIds));
        }

        return shops.stream()
                .map(ShopInfo::from)
                .toList();
    }
}
