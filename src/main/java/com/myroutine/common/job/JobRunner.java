package com.myroutine.common.job;

import com.myroutine.common.web.TraceIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {
    private final JobLock jobLock;

    public void run(String jobName, Supplier<Integer> task) {
        MDC.put(TraceIds.MDC_KEY, TraceIds.newId());
        MDC.put("job", jobName);
        try {
            long start = System.nanoTime();
            AtomicInteger count = new AtomicInteger();
            boolean ran = jobLock.runExclusively(jobName, () -> count.set(task.get()));
            if (ran) {
                log.info("job {} processed {} in {}ms", jobName, count.get(), (System.nanoTime() - start) / 1_000_000);
            } else {
                log.debug("job {} skipped: locked", jobName);
            }
        } catch (Exception e) {
            log.error("job {} failed", jobName, e);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
            MDC.remove("job");
        }
    }
}
