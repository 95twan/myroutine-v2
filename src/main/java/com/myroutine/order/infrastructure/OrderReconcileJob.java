package com.myroutine.order.infrastructure;

import com.myroutine.common.job.JobRunner;
import com.myroutine.order.application.OrderReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderReconcileJob {

    private final JobRunner jobRunner;
    private final OrderReconcileService orderReconcileService;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        jobRunner.run("reconcile-orders", orderReconcileService::reconcileBatch);
    }
}
