package com.songhg.veri.agent.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WP10 reporting control-plane switches, schema versions and bounded safety limits.
 */
@ConfigurationProperties(prefix = "veri-agent.reporting")
public record ReportingProperties(
        /** Enables the reporting control plane. */
        @DefaultValue("true") boolean enabled,
        /** Allows creating report snapshots from WP9 sanitized run exports. */
        @DefaultValue("true") boolean generateEnabled,
        /** Queues create/retry requests instead of generating in the HTTP request thread. */
        @DefaultValue("false") boolean asyncGenerationEnabled,
        /** Enables the managed background worker that processes queued report snapshots. */
        @DefaultValue("true") boolean generationWorkerEnabled,
        /** Fixed delay for the managed report generation worker loop. */
        @DefaultValue("5000") int generationWorkerIntervalMs,
        /** Startup delay for the managed report generation worker loop. */
        @DefaultValue("30000") int generationWorkerInitialDelayMs,
        /** Worker ID recorded in worker ticks and generation summaries. */
        @DefaultValue("wp10-report-worker") String generationWorkerId,
        /** Maximum queued reports claimed by one worker tick. */
        @DefaultValue("4") int generationWorkerBatchSize,
        /** Timeout for reports stuck in GENERATING before recovery marks them FAILED. */
        @DefaultValue("1800") int generationRunningTimeoutSeconds,
        /** Maximum stale GENERATING reports recovered by one worker tick. */
        @DefaultValue("50") int generationRecoveryBatchSize,
        /** Allows AI diagnosis through WP2; rule classification remains the fallback. */
        @DefaultValue("true") boolean diagnosisEnabled,
        /** Allows platform-local defect draft creation. */
        @DefaultValue("true") boolean defectDraftEnabled,
        /** Allows sanitized JSON/Markdown summary export. */
        @DefaultValue("true") boolean exportEnabled,
        /** Enables aggregate-only outbound report completion webhook delivery. */
        @DefaultValue("false") boolean webhookDeliveryEnabled,
        /** Global callback endpoint used for terminal report delivery. */
        String webhookDeliveryUrl,
        /** Enables HMAC signing headers for outbound report completion webhook delivery. */
        @DefaultValue("false") boolean webhookDeliverySignatureEnabled,
        /** Secret reference used for webhook signature resolution. */
        String webhookDeliverySecretRef,
        /** Delivery timeout for one webhook callback attempt. */
        @DefaultValue("5000") int webhookDeliveryTimeoutMs,
        /** Maximum evidence manifest items kept in one report. */
        @DefaultValue("200") int maxEvidenceItems,
        /** Maximum sanitized context characters sent to WP2. */
        @DefaultValue("12000") int maxDiagnosisContextChars,
        /** Maximum Markdown export characters returned by the API. */
        @DefaultValue("30000") int maxExportMarkdownChars,
        /** Report snapshot schema version. */
        @DefaultValue("wp10-report-v1") String schemaVersion,
        /** Export field-set version. */
        @DefaultValue("wp10-report-export-fields-v1") String fieldSetVersion
) {
    private static final int DEFAULT_MAX_EVIDENCE_ITEMS = 200;
    private static final int MAX_EVIDENCE_ITEMS = 10_000;
    private static final int DEFAULT_MAX_DIAGNOSIS_CONTEXT_CHARS = 12_000;
    private static final int MAX_DIAGNOSIS_CONTEXT_CHARS = 100_000;
    private static final int DEFAULT_MAX_EXPORT_MARKDOWN_CHARS = 30_000;
    private static final int MAX_EXPORT_MARKDOWN_CHARS = 200_000;
    private static final int DEFAULT_GENERATION_WORKER_INTERVAL_MS = 5_000;
    private static final int MAX_GENERATION_WORKER_INTERVAL_MS = 600_000;
    private static final int DEFAULT_GENERATION_WORKER_INITIAL_DELAY_MS = 30_000;
    private static final int MAX_GENERATION_WORKER_INITIAL_DELAY_MS = 3_600_000;
    private static final int DEFAULT_GENERATION_WORKER_BATCH_SIZE = 4;
    private static final int MAX_GENERATION_WORKER_BATCH_SIZE = 100;
    private static final int DEFAULT_GENERATION_RUNNING_TIMEOUT_SECONDS = 1_800;
    private static final int MAX_GENERATION_RUNNING_TIMEOUT_SECONDS = 86_400;
    private static final int DEFAULT_GENERATION_RECOVERY_BATCH_SIZE = 50;
    private static final int MAX_GENERATION_RECOVERY_BATCH_SIZE = 1_000;
    private static final int DEFAULT_WEBHOOK_DELIVERY_TIMEOUT_MS = 5_000;
    private static final int MAX_WEBHOOK_DELIVERY_TIMEOUT_MS = 120_000;
    private static final String DEFAULT_GENERATION_WORKER_ID = "wp10-report-worker";
    private static final String DEFAULT_SCHEMA_VERSION = "wp10-report-v1";
    private static final String DEFAULT_FIELD_SET_VERSION = "wp10-report-export-fields-v1";

    @ConstructorBinding
    public ReportingProperties {
    }

    public int effectiveMaxEvidenceItems() {
        return boundedPositive(maxEvidenceItems, DEFAULT_MAX_EVIDENCE_ITEMS, MAX_EVIDENCE_ITEMS);
    }

    public int effectiveMaxDiagnosisContextChars() {
        return boundedPositive(
                maxDiagnosisContextChars,
                DEFAULT_MAX_DIAGNOSIS_CONTEXT_CHARS,
                MAX_DIAGNOSIS_CONTEXT_CHARS
        );
    }

    public int effectiveMaxExportMarkdownChars() {
        return boundedPositive(
                maxExportMarkdownChars,
                DEFAULT_MAX_EXPORT_MARKDOWN_CHARS,
                MAX_EXPORT_MARKDOWN_CHARS
        );
    }

    public int effectiveGenerationWorkerIntervalMs() {
        return boundedPositive(
                generationWorkerIntervalMs,
                DEFAULT_GENERATION_WORKER_INTERVAL_MS,
                MAX_GENERATION_WORKER_INTERVAL_MS
        );
    }

    public int effectiveGenerationWorkerInitialDelayMs() {
        return boundedPositive(
                generationWorkerInitialDelayMs,
                DEFAULT_GENERATION_WORKER_INITIAL_DELAY_MS,
                MAX_GENERATION_WORKER_INITIAL_DELAY_MS
        );
    }

    public String effectiveGenerationWorkerId() {
        return boundedText(generationWorkerId, DEFAULT_GENERATION_WORKER_ID, 128);
    }

    public int effectiveGenerationWorkerBatchSize() {
        return boundedPositive(
                generationWorkerBatchSize,
                DEFAULT_GENERATION_WORKER_BATCH_SIZE,
                MAX_GENERATION_WORKER_BATCH_SIZE
        );
    }

    public int effectiveGenerationRunningTimeoutSeconds() {
        return boundedPositive(
                generationRunningTimeoutSeconds,
                DEFAULT_GENERATION_RUNNING_TIMEOUT_SECONDS,
                MAX_GENERATION_RUNNING_TIMEOUT_SECONDS
        );
    }

    public int effectiveGenerationRecoveryBatchSize() {
        return boundedPositive(
                generationRecoveryBatchSize,
                DEFAULT_GENERATION_RECOVERY_BATCH_SIZE,
                MAX_GENERATION_RECOVERY_BATCH_SIZE
        );
    }

    public String effectiveWebhookDeliveryUrl() {
        return boundedText(webhookDeliveryUrl, null, 512);
    }

    public String effectiveWebhookDeliverySecretRef() {
        return boundedText(webhookDeliverySecretRef, null, 256);
    }

    public int effectiveWebhookDeliveryTimeoutMs() {
        return boundedPositive(
                webhookDeliveryTimeoutMs,
                DEFAULT_WEBHOOK_DELIVERY_TIMEOUT_MS,
                MAX_WEBHOOK_DELIVERY_TIMEOUT_MS
        );
    }

    public String effectiveSchemaVersion() {
        return boundedText(schemaVersion, DEFAULT_SCHEMA_VERSION, 64);
    }

    public String effectiveFieldSetVersion() {
        return boundedText(fieldSetVersion, DEFAULT_FIELD_SET_VERSION, 96);
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
}
