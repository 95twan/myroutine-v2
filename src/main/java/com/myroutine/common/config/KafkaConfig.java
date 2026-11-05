package com.myroutine.common.config;

import com.myroutine.common.error.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, MeterRegistry registry,
                                                @Value("${myroutine.kafka.retry-initial-interval}") Duration retryInitialInterval) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) ->
                new TopicPartition(record.topic() + ".dlt", record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(retryInitialInterval.toMillis(), 2.0);
        backOff.setMaxAttempts(3);
        ConsumerRecordRecoverer counting = (record, ex) -> {
            recoverer.accept(record, ex);
            registry.counter("kafka_dlt_records_total", "topic", record.topic()).increment();
        };
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(counting, backOff);
        errorHandler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class, BusinessException.class);
        return errorHandler;
    }
}
