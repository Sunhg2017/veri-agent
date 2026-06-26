package com.songhg.veri.agent.testdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WP8 test data and account pool control-plane switches and limits.
 */
@ConfigurationProperties(prefix = "veri-agent.test-data")
public record TestDataProperties(
        /** Enables WP8 control-plane APIs. */
        @DefaultValue("true") boolean enabled,
        /** Enables the managed WP8 backend worker loop. */
        @DefaultValue("true") boolean workerEnabled,
        /** Fixed delay for the managed WP8 backend worker loop. */
        @DefaultValue("5000") int workerIntervalMs,
        /** Startup delay for the managed WP8 backend worker loop. */
        @DefaultValue("30000") int workerInitialDelayMs,
        /** Worker ID recorded in worker ticks and managed updates. */
        @DefaultValue("wp8-test-data-worker") String workerId,
        /** Maximum pending tasks claimed by one managed worker tick. */
        @DefaultValue("10") int workerTaskBatchSize,
        /** Maximum expired leases recovered by one managed worker tick. */
        @DefaultValue("50") int leaseRecoveryBatchSize,
        /** Maximum pooled accounts checked by one managed worker tick. */
        @DefaultValue("100") int accountHealthCheckBatchSize,
        /** Maximum records tracked by one data set. */
        @DefaultValue("10000") int recordMaxCount,
        /** Maximum sanitized summary bytes per record. */
        @DefaultValue("2048") int recordSummaryMaxBytes,
        /** Default account lease TTL in seconds. */
        @DefaultValue("1800") int defaultLeaseTtlSeconds,
        /** Maximum account lease TTL in seconds. */
        @DefaultValue("14400") int maxLeaseTtlSeconds,
        /** Allows destructive cleanup adapters; default stays off until dedicated release smoke exists. */
        @DefaultValue("false") boolean cleanupEnabled,
        /** Cleanup adapter mode. Supported values: DISABLED, HTTP. */
        @DefaultValue("DISABLED") String cleanupAdapterMode,
        /** HTTP endpoint used by the destructive cleanup adapter. */
        @DefaultValue("") String cleanupAdapterUrl,
        /** Bearer token used only when calling the cleanup adapter. */
        @DefaultValue("") String cleanupAdapterToken,
        /** Cleanup adapter HTTP timeout in milliseconds. */
        @DefaultValue("5000") int cleanupAdapterTimeoutMs,
        /** Enables managed business account provisioning from account-pool leasePolicy. */
        @DefaultValue("false") boolean accountProvisioningEnabled,
        /** Account provisioning adapter mode. Supported values: DISABLED, LOCAL_SECRET_REF, HTTP. */
        @DefaultValue("DISABLED") String accountProvisioningAdapterMode,
        /** HTTP endpoint used by the account provisioning adapter. */
        @DefaultValue("") String accountProvisioningAdapterUrl,
        /** Bearer token used only when calling the account provisioning adapter. */
        @DefaultValue("") String accountProvisioningAdapterToken,
        /** Account provisioning adapter HTTP timeout in milliseconds. */
        @DefaultValue("5000") int accountProvisioningAdapterTimeoutMs,
        /** Maximum READY account pools scanned for provisioning by one worker tick. */
        @DefaultValue("20") int accountProvisioningBatchSize,
        /** Allows redacted summary export. */
        @DefaultValue("true") boolean exportEnabled
) {
    private static final int DEFAULT_WORKER_INTERVAL_MS = 5_000;
    private static final int MAX_WORKER_INTERVAL_MS = 600_000;
    private static final int DEFAULT_WORKER_INITIAL_DELAY_MS = 30_000;
    private static final int MAX_WORKER_INITIAL_DELAY_MS = 3_600_000;
    private static final String DEFAULT_WORKER_ID = "wp8-test-data-worker";
    private static final int DEFAULT_WORKER_TASK_BATCH_SIZE = 10;
    private static final int MAX_WORKER_TASK_BATCH_SIZE = 500;
    private static final int DEFAULT_LEASE_RECOVERY_BATCH_SIZE = 50;
    private static final int MAX_LEASE_RECOVERY_BATCH_SIZE = 1_000;
    private static final int DEFAULT_ACCOUNT_HEALTH_CHECK_BATCH_SIZE = 100;
    private static final int MAX_ACCOUNT_HEALTH_CHECK_BATCH_SIZE = 1_000;
    private static final int DEFAULT_RECORD_MAX_COUNT = 10_000;
    private static final int MAX_RECORD_MAX_COUNT = 1_000_000;
    private static final int DEFAULT_RECORD_SUMMARY_MAX_BYTES = 2_048;
    private static final int MAX_RECORD_SUMMARY_MAX_BYTES = 65_536;
    private static final int DEFAULT_LEASE_TTL_SECONDS = 1_800;
    private static final int MAX_LEASE_TTL_SECONDS = 86_400;
    private static final int DEFAULT_ADAPTER_TIMEOUT_MS = 5_000;
    private static final int MAX_ADAPTER_TIMEOUT_MS = 60_000;
    private static final int DEFAULT_ACCOUNT_PROVISIONING_BATCH_SIZE = 20;
    private static final int MAX_ACCOUNT_PROVISIONING_BATCH_SIZE = 500;

    @ConstructorBinding
    public TestDataProperties {
    }

    public TestDataProperties(
            boolean enabled,
            int recordMaxCount,
            int recordSummaryMaxBytes,
            int defaultLeaseTtlSeconds,
            int maxLeaseTtlSeconds,
            boolean cleanupEnabled,
            boolean exportEnabled
    ) {
        this(
                enabled,
                true,
                DEFAULT_WORKER_INTERVAL_MS,
                DEFAULT_WORKER_INITIAL_DELAY_MS,
                DEFAULT_WORKER_ID,
                DEFAULT_WORKER_TASK_BATCH_SIZE,
                DEFAULT_LEASE_RECOVERY_BATCH_SIZE,
                DEFAULT_ACCOUNT_HEALTH_CHECK_BATCH_SIZE,
                recordMaxCount,
                recordSummaryMaxBytes,
                defaultLeaseTtlSeconds,
                maxLeaseTtlSeconds,
                cleanupEnabled,
                "DISABLED",
                "",
                "",
                DEFAULT_ADAPTER_TIMEOUT_MS,
                false,
                "DISABLED",
                "",
                "",
                DEFAULT_ADAPTER_TIMEOUT_MS,
                DEFAULT_ACCOUNT_PROVISIONING_BATCH_SIZE,
                exportEnabled
        );
    }

    public TestDataProperties(
            boolean enabled,
            boolean workerEnabled,
            int workerIntervalMs,
            int workerInitialDelayMs,
            String workerId,
            int workerTaskBatchSize,
            int leaseRecoveryBatchSize,
            int accountHealthCheckBatchSize,
            int recordMaxCount,
            int recordSummaryMaxBytes,
            int defaultLeaseTtlSeconds,
            int maxLeaseTtlSeconds,
            boolean cleanupEnabled,
            boolean exportEnabled
    ) {
        this(
                enabled,
                workerEnabled,
                workerIntervalMs,
                workerInitialDelayMs,
                workerId,
                workerTaskBatchSize,
                leaseRecoveryBatchSize,
                accountHealthCheckBatchSize,
                recordMaxCount,
                recordSummaryMaxBytes,
                defaultLeaseTtlSeconds,
                maxLeaseTtlSeconds,
                cleanupEnabled,
                "DISABLED",
                "",
                "",
                DEFAULT_ADAPTER_TIMEOUT_MS,
                false,
                "DISABLED",
                "",
                "",
                DEFAULT_ADAPTER_TIMEOUT_MS,
                DEFAULT_ACCOUNT_PROVISIONING_BATCH_SIZE,
                exportEnabled
        );
    }

    public int effectiveWorkerIntervalMs() {
        return boundedPositive(workerIntervalMs, DEFAULT_WORKER_INTERVAL_MS, MAX_WORKER_INTERVAL_MS);
    }

    public int effectiveWorkerInitialDelayMs() {
        return boundedPositive(
                workerInitialDelayMs,
                DEFAULT_WORKER_INITIAL_DELAY_MS,
                MAX_WORKER_INITIAL_DELAY_MS
        );
    }

    public String effectiveWorkerId() {
        return boundedText(workerId, DEFAULT_WORKER_ID, 128);
    }

    public int effectiveWorkerTaskBatchSize() {
        return boundedPositive(
                workerTaskBatchSize,
                DEFAULT_WORKER_TASK_BATCH_SIZE,
                MAX_WORKER_TASK_BATCH_SIZE
        );
    }

    public int effectiveLeaseRecoveryBatchSize() {
        return boundedPositive(
                leaseRecoveryBatchSize,
                DEFAULT_LEASE_RECOVERY_BATCH_SIZE,
                MAX_LEASE_RECOVERY_BATCH_SIZE
        );
    }

    public int effectiveAccountHealthCheckBatchSize() {
        return boundedPositive(
                accountHealthCheckBatchSize,
                DEFAULT_ACCOUNT_HEALTH_CHECK_BATCH_SIZE,
                MAX_ACCOUNT_HEALTH_CHECK_BATCH_SIZE
        );
    }

    public int effectiveRecordMaxCount() {
        return boundedPositive(recordMaxCount, DEFAULT_RECORD_MAX_COUNT, MAX_RECORD_MAX_COUNT);
    }

    public int effectiveRecordSummaryMaxBytes() {
        return boundedPositive(
                recordSummaryMaxBytes,
                DEFAULT_RECORD_SUMMARY_MAX_BYTES,
                MAX_RECORD_SUMMARY_MAX_BYTES
        );
    }

    public int effectiveDefaultLeaseTtlSeconds() {
        return Math.min(
                boundedPositive(defaultLeaseTtlSeconds, DEFAULT_LEASE_TTL_SECONDS, MAX_LEASE_TTL_SECONDS),
                effectiveMaxLeaseTtlSeconds()
        );
    }

    public int effectiveMaxLeaseTtlSeconds() {
        return boundedPositive(maxLeaseTtlSeconds, DEFAULT_LEASE_TTL_SECONDS, MAX_LEASE_TTL_SECONDS);
    }

    public String effectiveCleanupAdapterMode() {
        return boundedText(cleanupAdapterMode, "DISABLED", 32).toUpperCase(java.util.Locale.ROOT);
    }

    public String effectiveCleanupAdapterUrl() {
        return boundedNullableText(cleanupAdapterUrl, 2048);
    }

    public String effectiveCleanupAdapterToken() {
        return boundedNullableText(cleanupAdapterToken, 2048);
    }

    public int effectiveCleanupAdapterTimeoutMs() {
        return boundedPositive(cleanupAdapterTimeoutMs, DEFAULT_ADAPTER_TIMEOUT_MS, MAX_ADAPTER_TIMEOUT_MS);
    }

    public String effectiveAccountProvisioningAdapterMode() {
        return boundedText(accountProvisioningAdapterMode, "DISABLED", 32).toUpperCase(java.util.Locale.ROOT);
    }

    public String effectiveAccountProvisioningAdapterUrl() {
        return boundedNullableText(accountProvisioningAdapterUrl, 2048);
    }

    public String effectiveAccountProvisioningAdapterToken() {
        return boundedNullableText(accountProvisioningAdapterToken, 2048);
    }

    public int effectiveAccountProvisioningAdapterTimeoutMs() {
        return boundedPositive(accountProvisioningAdapterTimeoutMs, DEFAULT_ADAPTER_TIMEOUT_MS, MAX_ADAPTER_TIMEOUT_MS);
    }

    public int effectiveAccountProvisioningBatchSize() {
        return boundedPositive(
                accountProvisioningBatchSize,
                DEFAULT_ACCOUNT_PROVISIONING_BATCH_SIZE,
                MAX_ACCOUNT_PROVISIONING_BATCH_SIZE
        );
    }

    public boolean accountProvisioningRealAdapterReady() {
        return accountProvisioningEnabled()
                && "HTTP".equals(effectiveAccountProvisioningAdapterMode())
                && !effectiveAccountProvisioningAdapterUrl().isBlank();
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private static String boundedText(String value, String defaultValue, int maxLength) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String boundedNullableText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
