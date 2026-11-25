package com.myroutine.member.infrastructure;

import com.myroutine.common.job.JobRunner;
import com.myroutine.member.application.SessionCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private final JobRunner jobRunner;
    private final SessionCleanupService sessionCleanupService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        jobRunner.run("session-cleanup", sessionCleanupService::deleteExpired);
    }
}
