package com.myroutine.order.infrastructure;

import com.myroutine.order.api.event.OrderTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class OrderTopicConfig {
    @Bean
    public NewTopic orderExpiredTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_EXPIRED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderExpiredDltTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_EXPIRED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderLineConfirmedTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_LINE_CONFIRMED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderLineConfirmedDltTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_LINE_CONFIRMED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderLineRefundedTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_LINE_REFUNDED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderLineRefundedDltTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_LINE_REFUNDED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_PAID).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderPaidDltTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_PAID + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopOrderDeliveredTopic() {
        return TopicBuilder.name(OrderTopics.SHOP_ORDER_DELIVERED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopOrderDeliveredDltTopic() {
        return TopicBuilder.name(OrderTopics.SHOP_ORDER_DELIVERED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopOrderShippedTopic() {
        return TopicBuilder.name(OrderTopics.SHOP_ORDER_SHIPPED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shopOrderShippedDltTopic() {
        return TopicBuilder.name(OrderTopics.SHOP_ORDER_SHIPPED + ".dlt").partitions(3).replicas(1).build();
    }
}
