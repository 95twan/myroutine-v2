package com.myroutine.settlement.application;

import com.myroutine.member.api.WithdrawalPrecondition;
import com.myroutine.settlement.domain.SettlementItemRepository;
import com.myroutine.settlement.domain.SettlementRepository;
import com.myroutine.settlement.domain.SettlementStatus;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SettlementWithdrawalPrecondition implements WithdrawalPrecondition {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final ShopApi shopApi;

    @Override
    public List<String> violations(UUID memberId) {
        List<UUID> shopIds = shopApi.getShopIdsByOwner(memberId);
        if (shopIds.isEmpty()) {
            return List.of();
        }
        boolean unsettled = settlementItemRepository.existsUnsettledByShopIdIn(shopIds);
        boolean notPaid = settlementRepository.existsByShopIdInAndStatusNot(shopIds, SettlementStatus.PAID);
        if (unsettled || notPaid) {
            return List.of("PENDING_SETTLEMENT");
        }
        return List.of();
    }
}
