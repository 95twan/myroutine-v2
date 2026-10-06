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
public class OpenShopService {

    private final ShopRepository shopRepository;

    @Transactional
    public UUID open(UUID memberId, OpenShopCommand command) {
        if (shopRepository.existsByBusinessNumber(command.businessNumber())) {
            throw new BusinessException(ShopErrorCode.SHOP_BUSINESS_NUMBER_DUPLICATED);
        }
        Shop shop = Shop.open(
                memberId,
                command.name(),
                command.businessNumber(),
                command.email(),
                command.phone(),
                command.address()
        );
        Shop savedShop = shopRepository.save(shop);
        return savedShop.getId();
    }
}
