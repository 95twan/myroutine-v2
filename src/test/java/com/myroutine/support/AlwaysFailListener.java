package com.myroutine.support;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class AlwaysFailListener {

    private final ConcurrentHashMap<String, AtomicInteger> counter = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "test.always-fail.v1", groupId = "test-always-fail")
    public void onMessage(ConsumerRecord<String, String> record) {
        String key = record.key();
        counter.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        objectMapper.readTree(record.value());
        throw new IllegalStateException("always fail");
    }

    public int attempts(String key) {
        return counter.getOrDefault(key, new AtomicInteger(0)).get();
    }
}
