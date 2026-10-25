package com.myroutine.settlement.infrastructure;

import com.myroutine.common.config.BusinessProperties;
import com.myroutine.common.job.JobRunner;
import com.myroutine.settlement.application.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

@Component
@RequiredArgsConstructor
public class SettlementJob {

    private final JobRunner jobRunner;
    private final SettlementService settlementService;
    private final Clock clock;
    private final BusinessProperties properties;

    @Scheduled(cron = "0 0 3 5 * *", zone = "Asia/Seoul")
    public void run() {
        YearMonth lastMonth = YearMonth.now(clock.withZone(properties.businessZone())).minusMonths(1);
        jobRunner.run("settlement", () -> settlementService.run(lastMonth).settledShops());
    }
}
