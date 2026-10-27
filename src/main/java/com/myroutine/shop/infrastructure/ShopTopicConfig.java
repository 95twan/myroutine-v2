package com.myroutine.shop.infrastructure;

import com.myroutine.shop.api.event.ShopTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ShopTopicConfig {
    @Bean
    public NewTopic shopOpenedTopic() {
        return TopicBuilder.name(ShopTopics.SHOP_OPENED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopOpenedDltTopic() {
        return TopicBuilder.name(ShopTopics.SHOP_OPENED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopClosedTopic() {
        return TopicBuilder.name(ShopTopics.SHOP_CLOSED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopClosedDltTopic() {
        return TopicBuilder.name(ShopTopics.SHOP_CLOSED + ".dlt").partitions(3).replicas(1).build();
    }
}
