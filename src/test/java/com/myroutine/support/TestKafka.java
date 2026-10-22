package com.myroutine.support;

import com.myroutine.common.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestKafka {
    private final ObjectMapper objectMapper;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public List<ConsumerRecord<String, String>> readAll(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {   // try-with-resources: 끝나면 자동으로 close
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
            consumer.assign(partitions);            // 이 파티션들을 내가 직접 맡는다
            consumer.seekToBeginning(partitions);   // 처음 위치로

            int emptyPolls = 0;
            while (emptyPolls < 2) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                if (records.isEmpty()) {
                    emptyPolls++;
                } else {
                    emptyPolls = 0;                 // 메시지가 왔으면 다시 센다
                    records.forEach(result::add);
                }
            }
        }
        return result;
    }

    public List<ReceivedMessage> messages(String topic, String key) {
        return readAll(topic).stream()
                .filter(r -> key.equals(r.key()))
                .map(r -> new ReceivedMessage(key, r.partition(), r.value(), parseEnvelope(r.value())))
                .toList();
    }

    private EventEnvelope parseEnvelope(String value) {
        try {
            return objectMapper.readValue(value, EventEnvelope.class);
        } catch (JacksonException e) {
            return null;   // 봉투가 아닌 문자열
        }
    }
}
