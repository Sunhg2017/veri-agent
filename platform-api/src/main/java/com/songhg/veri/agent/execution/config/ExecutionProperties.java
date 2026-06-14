package com.songhg.veri.agent.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WP9 execution orchestration control-plane switches and limits.
 */
@ConfigurationProperties(prefix = "veri-agent.execution")
public record ExecutionProperties(
        /** Enables background queue claiming; default is off until WP9 scheduler smoke is available. */
        @DefaultValue("false") boolean schedulerEnabled,
        /** Enables signed external webhook ingress; default is off to avoid accidental external triggers. */
        @DefaultValue("false") boolean webhookEnabled,
        /** Enables cron metadata scanning; default is off for the first control-plane slice. */
        @DefaultValue("false") boolean cronEnabled,
        /** Allowed signed webhook timestamp skew in seconds. */
        @DefaultValue("300") long webhookClockSkewSeconds,
        /** Webhook secret cache TTL in seconds; set to 0 to resolve every request. */
        @DefaultValue("60") long webhookSecretCacheTtlSeconds,
        /** Fixed delay for the managed scheduler loop. */
        @DefaultValue("5000") int schedulerIntervalMs,
        /** Startup delay for the managed scheduler loop. */
        @DefaultValue("30000") int schedulerInitialDelayMs,
        /** Worker ID used by the managed scheduler loop. */
        @DefaultValue("wp9-managed-worker") String schedulerWorkerId,
        /** Maximum queued nodes claimed by one scheduler tick. */
        @DefaultValue("4") int schedulerTickBatchSize,
        /** Project-level concurrent run limit. */
        @DefaultValue("2") int maxConcurrentRunsPerProject,
        /** Per-run concurrent node limit. */
        @DefaultValue("4") int maxConcurrentNodesPerRun,
        /** Queue heartbeat timeout in seconds. */
        @DefaultValue("180") int nodeHeartbeatTimeoutSeconds,
        /** Default execution run timeout in seconds. */
        @DefaultValue("1800") int defaultRunTimeoutSeconds,
        /** Recovery scan batch size. */
        @DefaultValue("50") int recoveryBatchSize
) {
    private static final int DEFAULT_MAX_CONCURRENT_RUNS_PER_PROJECT = 2;
    private static final int MAX_CONCURRENT_RUNS_PER_PROJECT = 50;
    private static final int DEFAULT_SCHEDULER_INTERVAL_MS = 5_000;
    private static final long DEFAULT_WEBHOOK_CLOCK_SKEW_SECONDS = 300;
    private static final long MAX_WEBHOOK_CLOCK_SKEW_SECONDS = 86_400;
    private static final long MAX_WEBHOOK_SECRET_CACHE_TTL_SECONDS = 3_600;
    private static final int MAX_SCHEDULER_INTERVAL_MS = 600_000;
    private static final int DEFAULT_SCHEDULER_INITIAL_DELAY_MS = 30_000;
    private static final int MAX_SCHEDULER_INITIAL_DELAY_MS = 3_600_000;
    private static final int DEFAULT_SCHEDULER_TICK_BATCH_SIZE = 4;
    private static final int MAX_SCHEDULER_TICK_BATCH_SIZE = 100;
    private static final String DEFAULT_SCHEDULER_WORKER_ID = "wp9-managed-worker";
    private static final int DEFAULT_MAX_CONCURRENT_NODES_PER_RUN = 4;
    private static final int MAX_CONCURRENT_NODES_PER_RUN = 100;
    private static final int DEFAULT_NODE_HEARTBEAT_TIMEOUT_SECONDS = 180;
    private static final int MAX_NODE_HEARTBEAT_TIMEOUT_SECONDS = 86_400;
    private static final int DEFAULT_RUN_TIMEOUT_SECONDS = 1_800;
    private static final int MAX_RUN_TIMEOUT_SECONDS = 604_800;
    private static final int DEFAULT_RECOVERY_BATCH_SIZE = 50;
    private static final int MAX_RECOVERY_BATCH_SIZE = 1_000;

    @ConstructorBinding
    public ExecutionProperties {
    }

    public int effectiveMaxConcurrentRunsPerProject() {
        return boundedPositive(
                maxConcurrentRunsPerProject,
                DEFAULT_MAX_CONCURRENT_RUNS_PER_PROJECT,
                MAX_CONCURRENT_RUNS_PER_PROJECT
        );
    }

    public int effectiveSchedulerIntervalMs() {
        return boundedPositive(schedulerIntervalMs, DEFAULT_SCHEDULER_INTERVAL_MS, MAX_SCHEDULER_INTERVAL_MS);
    }

    public long effectiveWebhookClockSkewSeconds() {
        return boundedPositiveLong(
                webhookClockSkewSeconds,
                DEFAULT_WEBHOOK_CLOCK_SKEW_SECONDS,
                MAX_WEBHOOK_CLOCK_SKEW_SECONDS
        );
    }

    public long effectiveWebhookSecretCacheTtlSeconds() {
        if (webhookSecretCacheTtlSeconds <= 0) {
            return 0;
        }
        return Math.min(webhookSecretCacheTtlSeconds, MAX_WEBHOOK_SECRET_CACHE_TTL_SECONDS);
    }

    public int effectiveSchedulerInitialDelayMs() {
        return boundedPositive(
                schedulerInitialDelayMs,
                DEFAULT_SCHEDULER_INITIAL_DELAY_MS,
                MAX_SCHEDULER_INITIAL_DELAY_MS
        );
    }

    public String effectiveSchedulerWorkerId() {
        if (schedulerWorkerId == null || schedulerWorkerId.isBlank()) {
            return DEFAULT_SCHEDULER_WORKER_ID;
        }
        String trimmed = schedulerWorkerId.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    public int effectiveSchedulerTickBatchSize() {
        int bounded = boundedPositive(
                schedulerTickBatchSize,
                DEFAULT_SCHEDULER_TICK_BATCH_SIZE,
                MAX_SCHEDULER_TICK_BATCH_SIZE
        );
        return Math.min(bounded, effectiveMaxConcurrentNodesPerRun());
    }

    public int effectiveMaxConcurrentNodesPerRun() {
        return boundedPositive(
                maxConcurrentNodesPerRun,
                DEFAULT_MAX_CONCURRENT_NODES_PER_RUN,
                MAX_CONCURRENT_NODES_PER_RUN
        );
    }

    public int effectiveNodeHeartbeatTimeoutSeconds() {
        return boundedPositive(
                nodeHeartbeatTimeoutSeconds,
                DEFAULT_NODE_HEARTBEAT_TIMEOUT_SECONDS,
                MAX_NODE_HEARTBEAT_TIMEOUT_SECONDS
        );
    }

    public int effectiveDefaultRunTimeoutSeconds() {
        return boundedPositive(defaultRunTimeoutSeconds, DEFAULT_RUN_TIMEOUT_SECONDS, MAX_RUN_TIMEOUT_SECONDS);
    }

    public int effectiveRecoveryBatchSize() {
        return boundedPositive(recoveryBatchSize, DEFAULT_RECOVERY_BATCH_SIZE, MAX_RECOVERY_BATCH_SIZE);
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private static long boundedPositiveLong(long value, long defaultValue, long maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
