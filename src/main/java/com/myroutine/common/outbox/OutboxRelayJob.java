package com.myroutine.common.outbox;

import com.myroutine.common.job.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelayJob {
    private final OutboxRelay outboxRelay;
    private final JobRunner jobRunner;

    @Scheduled(fixedDelay = 1000)
    public void run() {
        jobRunner.run("outbox-relay", outboxRelay::relayOnce);
    }
}
