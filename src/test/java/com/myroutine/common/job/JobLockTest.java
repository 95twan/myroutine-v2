package com.myroutine.common.job;

import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;

class JobLockTest extends IntegrationTestSupport {

    private final JobLock jobLock;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JobLockTest(JobLock jobLock, JdbcTemplate jdbcTemplate) {
        this.jobLock = jobLock;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("같은 잡 이름으로 동시에 실행하면 하나만 작업을 실행한다.")
    void runExclusivelyOnlyOneWinsOnSameJobName() throws InterruptedException {
        List<ConcurrencyRunner.Result<Boolean>> results = ConcurrencyRunner.run(2, () -> jobLock.runExclusively("job", () -> {
            try {
                Thread.sleep(500);   // 락을 쥔 채로 다른 스레드가 시도할 시간을 준다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        assertThat(results).allMatch(ConcurrencyRunner.Result::ok);
        assertThat(results.stream().filter(ConcurrencyRunner.Result::value).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> !r.value()).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("작업이 예외를 던져도 락이 풀려 다음 호출은 작업을 실행한다.")
    void runExclusivelyReleasesLockWhenTaskThrows() {
        assertThatThrownBy(() -> jobLock.runExclusively("job", () -> { throw new RuntimeException(); }))
                .isInstanceOf(RuntimeException.class);

        Integer held = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'", Integer.class);
        assertThat(held).isZero();

        boolean second = jobLock.runExclusively("job", () -> {

        });

        assertThat(second).isTrue();
    }

    @Test
    @DisplayName("다른 잡 이름은 서로 막지 않는다.")
    void runExclusivelyDoesNotBlockDifferentJobName() throws InterruptedException {

        Callable<Boolean> task1 = () -> jobLock.runExclusively("job1", () -> {
                    try {
                        Thread.sleep(500);   // 락을 쥔 채로 다른 스레드가 시도할 시간을 준다
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        Callable<Boolean> task2 = () -> jobLock.runExclusively("job2", () -> {
            try {
                Thread.sleep(500);   // 락을 쥔 채로 다른 스레드가 시도할 시간을 준다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        List<ConcurrencyRunner.Result<Boolean>> results = ConcurrencyRunner.run(List.of(task1, task2));

        assertThat(results).allMatch(ConcurrencyRunner.Result::ok);
        assertThat(results.stream().filter(ConcurrencyRunner.Result::value).count()).isEqualTo(2);
    }
}
