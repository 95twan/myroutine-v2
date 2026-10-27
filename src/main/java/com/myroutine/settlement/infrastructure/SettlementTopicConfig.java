package com.myroutine.settlement.infrastructure;

import com.myroutine.settlement.api.event.SettlementTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class SettlementTopicConfig {

    @Bean
    public NewTopic settlementPaidTopic() {
        return TopicBuilder.name(SettlementTopics.SETTLEMENT_PAID).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic settlementPaidDltTopic() {
        return TopicBuilder.name(SettlementTopics.SETTLEMENT_PAID + ".dlt").partitions(3).replicas(1).build();
    }
}
