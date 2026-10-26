package com.myroutine.settlement.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.settlement.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final ShopApi shopApi;

    @Transactional(readOnly = true)
    public CursorPage<SettlementResult> getSettlements(UUID memberId, UUID shopId, String cursor, int size) {
        shopApi.verifyOwner(shopId, memberId);
        List<Settlement> settlements;
        if (cursor == null || cursor.isBlank()) {
            settlements = settlementRepository.findByShopIdFirstPage(shopId, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            settlements = settlementRepository.findByShopIdAfter(shopId, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (settlements.size() > size) {
            Settlement settlement = settlements.get(size - 1);
            nextCursor = new Cursor(settlement.getCreatedAt(), settlement.getId()).encode();
            settlements = settlements.subList(0, size);
        }

        return new CursorPage<>(settlements.stream().map(SettlementResult::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public SettlementDetailResult getSettlement(UUID memberId, UUID shopId, UUID settlementId, String cursor, int size) {
        shopApi.verifyOwner(shopId, memberId);
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow(
                () -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND)
        );

        if (!shopId.equals(settlement.getShopId())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }

        List<SettlementItemRow> settlementItems;
        if (cursor == null || cursor.isBlank()) {
            settlementItems = settlementItemRepository.findBySettlementIdFirstPage(settlementId, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            settlementItems = settlementItemRepository.findBySettlementIdAfter(settlementId, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (settlementItems.size() > size) {
            SettlementItemRow settlementItem = settlementItems.get(size - 1);
            nextCursor = new Cursor(settlementItem.getCreatedAt(), settlementItem.getId()).encode();
            settlementItems = settlementItems.subList(0, size);
        }

        CursorPage<SettlementItemResult> cursorPage = new CursorPage<>(settlementItems.stream().map(SettlementItemResult::from).toList(), nextCursor);
        return new SettlementDetailResult(SettlementResult.from(settlement), cursorPage);
    }
}
