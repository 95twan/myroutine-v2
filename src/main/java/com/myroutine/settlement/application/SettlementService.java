package com.myroutine.settlement.application;

import com.myroutine.common.config.BusinessProperties;
import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.settlement.api.event.SettlementPaidEvent;
import com.myroutine.settlement.api.event.SettlementTopics;
import com.myroutine.settlement.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementProperties settlementProperties;
    private final BusinessProperties businessProperties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final PayoutGateway payoutGateway;
    private final OutboxPublisher publisher;

    public SettlementRunResult run(YearMonth month) {
        SettlementPeriod period = SettlementPeriod.of(month);
        Instant from = period.fromInclusive(businessProperties.businessZone());
        Instant to = period.toExclusive(businessProperties.businessZone());
        UUID after = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<UUID> ids = settlementItemRepository.findShopIdsToSettle(from, to, after, 500);
        int settledShops = 0;
        while (!ids.isEmpty()) {
            for (UUID id : ids) {
                try {
                    boolean execute = tx.execute(s -> settleShop(id, period));
                    if (execute) settledShops++;
                } catch (Exception e) {
                    log.error("Failed to settle shop: {}", id, e);
                }
            }
            after = ids.getLast();
            ids = settlementItemRepository.findShopIdsToSettle(from, to, after, 500);
        }
        PayoutSummary payout = payoutAll();
        return new SettlementRunResult(settledShops, payout.paid(), payout.payoutFailed());
    }

    public boolean settleShop(UUID shopId, SettlementPeriod period) {
        UUID settlementId = Ids.newId();
        Instant now = clock.instant();
        int count = settlementRepository.insertIfAbsent(
                settlementId,
                shopId,
                period.start(),
                period.end(),
                settlementProperties.feeRateBp(),
                now
        );
        if (count == 0) {
            return false;
        }
        settlementItemRepository.assign(
                settlementId,
                shopId,
                period.fromInclusive(businessProperties.businessZone()),
                period.toExclusive(businessProperties.businessZone()),
                now
        );
        SettlementSum sum = settlementItemRepository.sumBySettlementId(settlementId);
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow(
                () -> new IllegalStateException("정산이 존재하지 않습니다. settlementId: " + settlementId)
        );
        settlement.calculate(Money.of(sum.getGrossAmount()), settlementProperties.feeRateBp(), sum.getItemCount());

        return true;
    }

    public PayoutSummary payoutAll() {
        UUID after = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<UUID> ids = settlementRepository.findIdsToPay(List.of(SettlementStatus.CALCULATED, SettlementStatus.PAYOUT_FAILED), after, Limit.of(500));
        int paid = 0;
        int payoutFailed = 0;
        while (!ids.isEmpty()) {
            for (UUID id : ids) {
                try {
                    Settlement s = tx.execute(_ -> {
                        Settlement settlement = settlementRepository.findById(id).orElseThrow();
                        if (settlement.getNetAmount().amount() == 0) {
                            settlement.markPaid(null, clock.instant());
                            SettlementPaidEvent event = new SettlementPaidEvent(
                                    settlement.getId(),
                                    settlement.getShopId(),
                                    settlement.getNetAmount().amount(),
                                    settlement.getPeriodStart(),
                                    settlement.getPeriodEnd()
                            );
                            publisher.publish(SettlementTopics.SETTLEMENT_PAID, "SETTLEMENT", settlement.getId(), event);
                        }
                        return settlement;
                    });
                    if (s.getStatus() == SettlementStatus.PAID) {
                        paid++;
                        continue;
                    }

                    String payoutReference;
                    try {
                        payoutReference = payoutGateway.payout(id, s.getShopId(), s.getNetAmount().amount());
                    } catch (Exception e) {
                        log.warn("Payout failed. settlementId={} shopId={}", id, s.getShopId(), e);
                        tx.executeWithoutResult(_ ->
                                settlementRepository.findById(id).orElseThrow().markPayoutFailed(e.getMessage() != null ? e.getMessage() : e.toString()));
                        payoutFailed++;
                        continue;
                    }

                    tx.executeWithoutResult(_ -> {
                        Settlement settlement = settlementRepository.findById(id).orElseThrow();
                        settlement.markPaid(payoutReference, clock.instant());
                        SettlementPaidEvent event = new SettlementPaidEvent(
                                settlement.getId(),
                                settlement.getShopId(),
                                settlement.getNetAmount().amount(),
                                settlement.getPeriodStart(),
                                settlement.getPeriodEnd()
                        );
                        publisher.publish(SettlementTopics.SETTLEMENT_PAID, "SETTLEMENT", settlement.getId(), event);
                    });
                    paid++;
                } catch (Exception e) {
                    log.error("Failed to pay settlement: {}", id, e);
                }
            }
            after = ids.getLast();
            ids = settlementRepository.findIdsToPay(List.of(SettlementStatus.CALCULATED, SettlementStatus.PAYOUT_FAILED), after, Limit.of(500));
        }
        return new PayoutSummary(paid, payoutFailed);
    }

}
