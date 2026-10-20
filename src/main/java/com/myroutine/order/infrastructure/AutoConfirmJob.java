package com.myroutine.order.infrastructure;

import com.myroutine.common.job.JobRunner;
import com.myroutine.order.application.AutoConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoConfirmJob {
    private final AutoConfirmService autoConfirmService;
    private final JobRunner jobRunner;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void run() {
        jobRunner.run("auto-confirm", autoConfirmService::confirmBatch);
    }
}
