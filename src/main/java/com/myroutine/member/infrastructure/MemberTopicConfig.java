package com.myroutine.member.infrastructure;

import com.myroutine.member.api.event.MemberTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MemberTopicConfig {
    @Bean
    public NewTopic memberRegisteredTopic() {
        return TopicBuilder.name(MemberTopics.MEMBER_REGISTERED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic memberRegisteredDltTopic() {
        return TopicBuilder.name(MemberTopics.MEMBER_REGISTERED + ".dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic memberWithdrawnTopic() {
        return TopicBuilder.name(MemberTopics.MEMBER_WITHDRAWN).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic memberWithdrawnDltTopic() {
        return TopicBuilder.name(MemberTopics.MEMBER_WITHDRAWN + ".dlt").partitions(3).replicas(1).build();
    }
}
