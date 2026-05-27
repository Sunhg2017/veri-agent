package com.songhg.veri.agent.common.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "veri-agent.events")
public record PlatformEventProperties(
        /** 本地事件总线 worker 线程数，仅在未启用 kafka profile 时使用 */
        int localWorkerThreads,
        /** Kafka 事件总线配置 */
        Kafka kafka
) {

    private static final int DEFAULT_LOCAL_WORKER_THREADS = 2;
    private static final String DEFAULT_CONSUMER_GROUP = "platform-api";
    private static final String DEFAULT_MODEL_INVOCATION_JOB_TOPIC = "veri-agent.model-invocation-job-requested";
    private static final String DEFAULT_AUDIT_LOG_RECORDED_TOPIC = "veri-agent.audit-log-recorded";
    private static final String DEFAULT_DOCUMENT_INPUT_IMPORT_REQUESTED_TOPIC = "veri-agent.document-input-import-requested";
    private static final String DEFAULT_DOCUMENT_INPUT_PUBLISH_REQUESTED_TOPIC = "veri-agent.document-input-publish-requested";
    private static final String DEFAULT_DOCUMENT_INPUT_WEBHOOK_ACCEPTED_TOPIC = "veri-agent.document-input-webhook-accepted";
    private static final String DEFAULT_TEST_DESIGN_GENERATION_REQUESTED_TOPIC = "veri-agent.test-design-generation-requested";

    public int safeLocalWorkerThreads() {
        return Math.max(1, localWorkerThreads <= 0 ? DEFAULT_LOCAL_WORKER_THREADS : localWorkerThreads);
    }

    public Kafka safeKafka() {
        return kafka == null ? new Kafka(null, null, 0, (short) 0) : kafka;
    }

    public String modelInvocationJobRequestedTopic() {
        return safeKafka().safeTopics().modelInvocationJobRequestedTopic();
    }

    public String auditLogRecordedTopic() {
        return safeKafka().safeTopics().auditLogRecordedTopic();
    }

    public String documentInputImportRequestedTopic() {
        return safeKafka().safeTopics().documentInputImportRequestedTopic();
    }

    public String documentInputPublishRequestedTopic() {
        return safeKafka().safeTopics().documentInputPublishRequestedTopic();
    }

    public String documentInputWebhookAcceptedTopic() {
        return safeKafka().safeTopics().documentInputWebhookAcceptedTopic();
    }

    public String testDesignGenerationRequestedTopic() {
        return safeKafka().safeTopics().testDesignGenerationRequestedTopic();
    }

    public String kafkaConsumerGroup() {
        return safeKafka().consumerGroupValue();
    }

    public int kafkaTopicPartitions() {
        return Math.max(1, safeKafka().topicPartitions() <= 0 ? 3 : safeKafka().topicPartitions());
    }

    public short kafkaTopicReplicas() {
        return (short) Math.max(1, safeKafka().topicReplicas() <= 0 ? 1 : safeKafka().topicReplicas());
    }

    public record Kafka(
            /** Kafka consumer group for platform-api event consumers */
            String consumerGroup,
            /** Kafka topic names */
            Topics topics,
            /** Auto-created topic partitions for local/preprod bootstrap */
            int topicPartitions,
            /** Auto-created topic replica count */
            short topicReplicas
    ) {

        private Topics safeTopics() {
            return topics == null ? new Topics(null, null, null, null, null, null) : topics;
        }

        private String consumerGroupValue() {
            return StringUtils.hasText(consumerGroup) ? consumerGroup : DEFAULT_CONSUMER_GROUP;
        }
    }

    public record Topics(
            /** WP2 async model invocation job request topic */
            String modelInvocationJobRequested,
            /** Platform audit log append topic */
            String auditLogRecorded,
            /** WP4 document import parse request topic */
            String documentInputImportRequested,
            /** WP4 confirmed candidate publish request topic */
            String documentInputPublishRequested,
            /** WP4 accepted webhook processing topic */
            String documentInputWebhookAccepted,
            /** WP5 queued test design generation request topic */
            String testDesignGenerationRequested
    ) {

        private String modelInvocationJobRequestedTopic() {
            return StringUtils.hasText(modelInvocationJobRequested)
                    ? modelInvocationJobRequested
                    : DEFAULT_MODEL_INVOCATION_JOB_TOPIC;
        }

        private String auditLogRecordedTopic() {
            return StringUtils.hasText(auditLogRecorded)
                    ? auditLogRecorded
                    : DEFAULT_AUDIT_LOG_RECORDED_TOPIC;
        }

        private String documentInputImportRequestedTopic() {
            return StringUtils.hasText(documentInputImportRequested)
                    ? documentInputImportRequested
                    : DEFAULT_DOCUMENT_INPUT_IMPORT_REQUESTED_TOPIC;
        }

        private String documentInputPublishRequestedTopic() {
            return StringUtils.hasText(documentInputPublishRequested)
                    ? documentInputPublishRequested
                    : DEFAULT_DOCUMENT_INPUT_PUBLISH_REQUESTED_TOPIC;
        }

        private String documentInputWebhookAcceptedTopic() {
            return StringUtils.hasText(documentInputWebhookAccepted)
                    ? documentInputWebhookAccepted
                    : DEFAULT_DOCUMENT_INPUT_WEBHOOK_ACCEPTED_TOPIC;
        }

        private String testDesignGenerationRequestedTopic() {
            return StringUtils.hasText(testDesignGenerationRequested)
                    ? testDesignGenerationRequested
                    : DEFAULT_TEST_DESIGN_GENERATION_REQUESTED_TOPIC;
        }
    }
}
