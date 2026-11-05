package com.myroutine.payment.application;

import com.myroutine.payment.domain.PaymentRepository;
import com.myroutine.payment.domain.PaymentStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PaymentMetrics {

    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final AtomicLong unknown = new AtomicLong();
    private final AtomicLong unknownOldestAge = new AtomicLong();

    public PaymentMetrics(MeterRegistry registry, PaymentRepository paymentRepository, Clock clock) {
        this.clock = clock;
        this.paymentRepository = paymentRepository;
        Gauge.builder("payment_unknown_count", unknown, AtomicLong::doubleValue).register(registry);
        Gauge.builder("payment_unknown_oldest_age_seconds", unknownOldestAge, AtomicLong::doubleValue).register(registry);
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        unknown.set(paymentRepository.countByStatus(PaymentStatus.UNKNOWN));
        unknownOldestAge.set(paymentRepository.findOldestCreatedAtByStatus(PaymentStatus.UNKNOWN).map(t -> Duration.between(t, clock.instant()).toSeconds()).orElse(0L));
    }
}
