package com.myroutine.product.infrastructure;

import com.myroutine.product.api.event.ProductTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ProductTopicConfig {
    @Bean
    public NewTopic productDiscontinuedTopic() {
        return TopicBuilder.name(ProductTopics.PRODUCT_DISCONTINUED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic productDiscontinuedDltTopic() {
        return TopicBuilder.name(ProductTopics.PRODUCT_DISCONTINUED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic productPriceChangedTopic() {
        return TopicBuilder.name(ProductTopics.PRODUCT_PRICE_CHANGED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic productPriceChangedDltTopic() {
        return TopicBuilder.name(ProductTopics.PRODUCT_PRICE_CHANGED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic productUpsertedTopic() {
        return TopicBuilder.name(ProductTopics.PRODUCT_UPSERTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic productUpsertedDltTopic() {
        return TopicBuilder.name(ProductTopics.PRODUCT_UPSERTED + ".dlt").partitions(3).replicas(1).build();
    }
}
