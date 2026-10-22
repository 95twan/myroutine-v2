package com.myroutine.shop.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.shop.api.ShopClosePrecondition;
import com.myroutine.shop.api.event.ShopClosedEvent;
import com.myroutine.shop.api.event.ShopTopics;
import com.myroutine.shop.domain.Shop;
import com.myroutine.shop.domain.ShopErrorCode;
import com.myroutine.shop.domain.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloseShopService {
    private final ShopRepository shopRepository;
    private final List<ShopClosePrecondition> preconditions;
    private final OutboxPublisher publisher;
    private final Clock clock;

    @Transactional
    public void close(UUID memberId, UUID shopId) {
        Shop shop = shopRepository.findById(shopId).orElseThrow(
                () -> new BusinessException(ShopErrorCode.SHOP_NOT_FOUND)
        );
        shop.verifyOwner(memberId);
        shop.verifyActive();
        preconditions.forEach(precondition -> precondition.check(shopId));
        shop.close(clock.instant());
        publisher.publish(ShopTopics.SHOP_CLOSED, "SHOP", shopId, new ShopClosedEvent(shopId, memberId));
    }
}
