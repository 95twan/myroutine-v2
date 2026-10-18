package com.myroutine.order.application;

import com.myroutine.order.domain.RefundRepository;
import com.myroutine.order.domain.RefundStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRecoveryService {
    private final RefundRepository refundRepository;
    private final RefundCompleter refundCompleter;
    private final RefundProcessor refundProcessor;
    private final Clock clock;

    public int recoverBatch() {
        Instant now = clock.instant();
        int count = 0;
        List<UUID> refundIds = refundRepository.findStuckIds(RefundStatus.PG_CANCELLED, now.minus(Duration.ofMinutes(5)), Limit.of(100));
        for (UUID refundId : refundIds) {
            try {
                RefundStatus status = refundCompleter.complete(refundId);
                if (status == RefundStatus.COMPLETED) {
                    count++;
                }
            } catch (Exception e) {
                log.error("환불 상태 변경 실패 refundId={}", refundId, e);
            }
        }

        refundIds = refundRepository.findStuckIds(RefundStatus.APPROVED, now.minus(Duration.ofMinutes(5)), Limit.of(100));
        for (UUID refundId : refundIds) {
            try {
                RefundStatus status = refundProcessor.process(refundId);
                if (status == RefundStatus.COMPLETED) {
                    count++;
                } else if (status == RefundStatus.FAILED) {
                    log.warn("복구 중 PG가 취소를 거절했다. refundId={}", refundId);
                }
            } catch (Exception e) {
                log.error("환불 상태 변경 실패 refundId={}", refundId, e);
            }
        }
        return count;
    }
}
