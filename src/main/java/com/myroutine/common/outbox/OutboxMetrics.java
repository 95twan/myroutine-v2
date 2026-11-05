package com.myroutine.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class OutboxMetrics {
    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong ready = new AtomicLong();
    private final AtomicLong oldestAge = new AtomicLong();
    private final AtomicLong dead = new AtomicLong();

    public OutboxMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("outbox_ready_count", ready, AtomicLong::doubleValue).register(registry);
        Gauge.builder("outbox_oldest_age_seconds", oldestAge, AtomicLong::doubleValue).register(registry);
        Gauge.builder("outbox_dead_count", dead, AtomicLong::doubleValue).register(registry);
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        ready.set(jdbcTemplate.queryForObject("SELECT count(*) FROM common.outbox_event WHERE status = 'READY'", Long.class));
        oldestAge.set(jdbcTemplate.queryForObject("SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)::bigint FROM common.outbox_event WHERE status = 'READY'", Long.class));
        dead.set(jdbcTemplate.queryForObject("SELECT count(*) FROM common.outbox_event WHERE status = 'DEAD'", Long.class));
    }
}
