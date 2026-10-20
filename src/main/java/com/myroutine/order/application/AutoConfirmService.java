package com.myroutine.order.application;

import com.myroutine.order.domain.AutoConfirmTarget;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.ShopOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoConfirmService {
    private static final Duration AUTO_CONFIRM_DELAY = Duration.ofDays(7);

    private final OrderRepository orderRepository;
    private final TransactionTemplate tx;
    private final Clock clock;

    public int confirmBatch() {
        Instant now = clock.instant();
        Instant before = now.minus(AUTO_CONFIRM_DELAY);

        int count = 0;

        List<AutoConfirmTarget> targets = orderRepository.findAutoConfirmTargetsFirstPage(ShopOrderStatus.DELIVERED, before, Limit.of(100));

        while (!targets.isEmpty()) {
            for (AutoConfirmTarget target : targets) {
                try {
                    count += tx.execute(s ->
                            orderRepository.findByShopOrderId(target.shopOrderId()).orElseThrow().autoConfirm(target.shopOrderId(), now)
                    );
                } catch (Exception e) {
                    log.error("자동 구매확정 실패 shopOrderId={}", target.shopOrderId(), e);
                }
            }
            if (targets.size() < 100) {
                break;
            }
            AutoConfirmTarget last = targets.getLast();
            targets = orderRepository.findAutoConfirmTargets(ShopOrderStatus.DELIVERED, before, last.deliveredAt(), last.shopOrderId(), Limit.of(100));
        }
        return count;
    }
}
