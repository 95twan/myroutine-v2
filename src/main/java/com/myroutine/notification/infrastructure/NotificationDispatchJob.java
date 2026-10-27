package com.myroutine.notification.infrastructure;

import com.myroutine.common.job.JobRunner;
import com.myroutine.notification.application.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDispatchJob {
    private final NotificationDispatcher dispatcher;
    private final JobRunner jobRunner;

    @Scheduled(fixedDelay = 10_000)
    public void run() {
        jobRunner.run("dispatch-notifications", dispatcher::dispatchBatch);
    }
}
