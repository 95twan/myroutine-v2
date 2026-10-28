package com.myroutine.settlement.application;

import com.myroutine.common.model.Ids;
import com.myroutine.order.api.event.OrderLineConfirmedEvent;
import com.myroutine.settlement.domain.SettlementItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class SettlementItemService {
    private final SettlementItemRepository settlementItemRepository;
    private final Clock clock;

    @Transactional
    public void register(OrderLineConfirmedEvent event) {
        settlementItemRepository.insertIgnoringConflict(
                Ids.newId(),
                event.orderLineId(),
                event.shopId(),
                event.orderId(),
                event.shopOrderId(),
                event.productId(),
                event.productName(),
                event.quantity(),
                event.amount(),
                event.confirmedAt(),
                clock.instant()
        );
    }
}
