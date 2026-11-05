package com.myroutine.product.infrastructure;

import com.myroutine.common.job.JobRunner;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class StockCheckJob {
    private final JobRunner jobRunner;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong stockBalanceMismatch = new AtomicLong();

    public StockCheckJob(JobRunner jobRunner, MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jobRunner = jobRunner;
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("stock_balance_mismatch_count", stockBalanceMismatch, AtomicLong::doubleValue).register(registry);
    }

    @Scheduled(cron = "${myroutine.stock-check.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void run() {
        jobRunner.run("stock-check", this::check);
    }

    public int check() {
        List<UUID> mismatchProductIds = jdbcTemplate.queryForList("""
                        SELECT s.product_id
                        FROM product.stock s
                        LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_movement WHERE type IN ('RECEIVE','ADJUST') GROUP BY product_id) m ON m.product_id = s.product_id
                        LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_reservation WHERE status = 'HELD' GROUP BY product_id) r ON r.product_id = s.product_id
                        WHERE s.received <> COALESCE(m.q, 0) OR s.reserved <> COALESCE(r.q, 0)
                        """,
                UUID.class
        );

        mismatchProductIds.forEach(id -> log.error("stock mismatch productId={}", id));
        stockBalanceMismatch.set(mismatchProductIds.size());
        return mismatchProductIds.size();
    }
}
