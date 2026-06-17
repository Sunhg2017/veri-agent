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

    @Bean
    NewTopic auditLogRecordedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.auditLogRecordedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }

    @Bean
    NewTopic documentInputImportRequestedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.documentInputImportRequestedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }

    @Bean
    NewTopic documentInputPublishRequestedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.documentInputPublishRequestedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }

    @Bean
    NewTopic documentInputWebhookAcceptedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.documentInputWebhookAcceptedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }

    @Bean
    NewTopic testDesignGenerationRequestedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.testDesignGenerationRequestedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }

    @Bean
    NewTopic testDesignPublishRequestedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.testDesignPublishRequestedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }

    @Bean
    NewTopic reportWebhookDeliveryRequestedTopic(PlatformEventProperties properties) {
        return TopicBuilder.name(properties.reportWebhookDeliveryRequestedTopic())
                .partitions(properties.kafkaTopicPartitions())
                .replicas(properties.kafkaTopicReplicas())
                .build();
    }
}
