package com.myroutine.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TestKafkaTopics {

    @Bean
    public NewTopic testOutboxRelay() {
        return TopicBuilder.name("test.outbox-relay.v1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic testAlwaysFail() {
        return TopicBuilder.name("test.always-fail.v1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic testAlwaysFailDlt() {
        return TopicBuilder.name("test.always-fail.v1.dlt").partitions(3).replicas(1).build();
    }
}
