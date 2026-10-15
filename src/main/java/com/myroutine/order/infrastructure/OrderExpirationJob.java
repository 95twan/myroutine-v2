package com.myroutine.order.infrastructure;

import com.myroutine.common.job.JobRunner;
import com.myroutine.order.application.OrderExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderExpirationJob {
    private final OrderExpirationService orderExpirationService;
    private final JobRunner jobRunner;

    @Scheduled(fixedDelay = 60_000)
    void run() {
        jobRunner.run("expire-orders", orderExpirationService::expireBatch);
    }
}
