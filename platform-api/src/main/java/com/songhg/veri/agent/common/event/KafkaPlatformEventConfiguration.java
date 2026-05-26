package com.songhg.veri.agent.common.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
@Profile("kafka")
public class KafkaPlatformEventConfiguration {

    @Bean
    NewTopic modelInvocationJobRequestedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.modelInvocationJobRequestedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }
}
