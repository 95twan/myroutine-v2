package com.myroutine.shop.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.shop.domain.Shop;
import com.myroutine.shop.domain.ShopErrorCode;
import com.myroutine.shop.domain.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateShopService {

    private final ShopRepository shopRepository;

    @Transactional
    public ShopResult update(UUID memberId, UUID shopId, UpdateShopCommand command) {
        Shop shop = shopRepository.findById(shopId).orElseThrow(
                () -> new BusinessException(ShopErrorCode.SHOP_NOT_FOUND)
        );
        shop.verifyOwner(memberId);
        shop.verifyActive();
        shop.update(
                command.name(),
                command.email(),
                command.phone(),
                command.address()
        );

        return ShopResult.from(shop);
    }
}
