package com.myroutine.order.infrastructure;

import com.myroutine.common.job.JobRunner;
import com.myroutine.order.application.RefundRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundRecoveryJob {
    private final RefundRecoveryService refundRecoveryService;
    private final JobRunner jobRunner;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        jobRunner.run("recover-refunds", refundRecoveryService::recoverBatch);
    }
}
