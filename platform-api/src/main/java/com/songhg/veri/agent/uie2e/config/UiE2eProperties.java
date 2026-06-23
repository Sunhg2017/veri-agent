package com.songhg.veri.agent.uie2e.config;

import java.util.List;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * WP7 UI/E2E control-plane switches and bounded safety limits.
 */
@ConfigurationProperties(prefix = "veri-agent.ui-e2e")
public record UiE2eProperties(
        /** Enables the WP7 control plane. */
        @DefaultValue("true") boolean enabled,
        /** Enables managed browser execution; default stays off until runner smoke exists. */
        @DefaultValue("false") boolean runnerEnabled,
        /** Runner mode summary returned by health. */
        @DefaultValue("disabled") String runnerMode,
        /** Default timeout for one UI/E2E run. */
        @DefaultValue("300") int defaultTimeoutSeconds,
        /** Maximum timeout allowed for one UI/E2E run. */
        @DefaultValue("1800") int maxTimeoutSeconds,
        /** Maximum scene count accepted by one run request. */
        @DefaultValue("1") int maxScenesPerRun,
        /** Maximum artifact size captured for one artifact reference. */
        @DefaultValue("20971520") long maxArtifactSizeBytes,
        /** Maximum artifact count captured for one run. */
        @DefaultValue("20") int maxArtifactCount,
        /** Maximum runner concurrency allowed by one service instance. */
        @DefaultValue("2") int maxConcurrency,
        /** Allowed base URL host list; values stay hidden from health responses. */
        List<String> allowlistBaseUrls,
        /** Whether screenshot capture is enabled. */
        @DefaultValue("true") boolean captureScreenshotEnabled,
        /** Whether video capture is enabled. */
        @DefaultValue("false") boolean captureVideoEnabled,
        /** Whether HAR capture is enabled. */
        @DefaultValue("false") boolean captureHarEnabled,
        /** Whether trace capture is enabled. */
        @DefaultValue("true") boolean captureTraceEnabled,
        /** Whether JUnit XML capture is enabled. */
        @DefaultValue("false") boolean captureJunitXmlEnabled,
        /** Whether run summary export is enabled. */
        @DefaultValue("true") boolean exportEnabled,
        /** Node executable used by the local real-browser runner. */
        @DefaultValue("node") String runnerNodeCommand,
        /** Node modules directory that provides the Playwright runtime. */
        @DefaultValue("../portal-web/node_modules") String runnerNodeModulesDir,
        /** External HTTP worker run endpoint used by isolated UI/E2E execution. */
        @DefaultValue("") String runnerWorkerUrl,
        /** Optional external HTTP worker cancel endpoint used for best-effort run cancellation. */
        @DefaultValue("") String runnerWorkerCancelUrl,
        /** Optional bearer token used when calling the external HTTP worker. */
        @DefaultValue("") String runnerWorkerToken,
        /** Connect timeout used when establishing a remote worker HTTP session. */
        @DefaultValue("10") int runnerWorkerConnectTimeoutSeconds,
        /** Controlled local root used to persist downloadable raw artifacts. */
        @DefaultValue("") String artifactStorageDir,
        /** Enables destructive cleanup for unreferenced local artifacts. */
        @DefaultValue("false") boolean artifactCleanupEnabled,
        /** Retention window in hours before unreferenced artifacts can be deleted. */
        @DefaultValue("168") int artifactCleanupRetentionHours,
        /** Maximum file deletions performed by one cleanup tick. */
        @DefaultValue("100") int artifactCleanupBatchSize
) {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_TIMEOUT_SECONDS = 86_400;
    private static final int DEFAULT_MAX_SCENES_PER_RUN = 1;
    private static final int MAX_MAX_SCENES_PER_RUN = 100;
    private static final long DEFAULT_MAX_ARTIFACT_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_ARTIFACT_SIZE_BYTES = 1024L * 1024L * 1024L;
    private static final int DEFAULT_MAX_ARTIFACT_COUNT = 20;
    private static final int MAX_MAX_ARTIFACT_COUNT = 500;
    private static final int DEFAULT_MAX_CONCURRENCY = 2;
    private static final int MAX_MAX_CONCURRENCY = 100;
    private static final int DEFAULT_RUNNER_WORKER_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int MAX_RUNNER_WORKER_CONNECT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_ARTIFACT_CLEANUP_RETENTION_HOURS = 168;
    private static final int MAX_ARTIFACT_CLEANUP_RETENTION_HOURS = 24 * 365;
    private static final int DEFAULT_ARTIFACT_CLEANUP_BATCH_SIZE = 100;
    private static final int MAX_ARTIFACT_CLEANUP_BATCH_SIZE = 5000;

    @ConstructorBinding
    public UiE2eProperties {
        allowlistBaseUrls = allowlistBaseUrls == null ? List.of() : List.copyOf(allowlistBaseUrls);
    }

    public UiE2eProperties(
            boolean enabled,
            boolean runnerEnabled,
            String runnerMode,
            int defaultTimeoutSeconds,
            int maxTimeoutSeconds,
            int maxScenesPerRun,
            long maxArtifactSizeBytes,
            int maxArtifactCount,
            int maxConcurrency,
            List<String> allowlistBaseUrls,
            boolean captureScreenshotEnabled,
            boolean captureVideoEnabled,
            boolean captureHarEnabled,
            boolean captureTraceEnabled,
            boolean captureJunitXmlEnabled,
            boolean exportEnabled,
            String runnerNodeCommand,
            String runnerNodeModulesDir,
            String artifactStorageDir
    ) {
        this(
                enabled,
                runnerEnabled,
                runnerMode,
                defaultTimeoutSeconds,
                maxTimeoutSeconds,
                maxScenesPerRun,
                maxArtifactSizeBytes,
                maxArtifactCount,
                maxConcurrency,
                allowlistBaseUrls,
                captureScreenshotEnabled,
                captureVideoEnabled,
                captureHarEnabled,
                captureTraceEnabled,
                captureJunitXmlEnabled,
                exportEnabled,
                runnerNodeCommand,
                runnerNodeModulesDir,
                "",
                "",
                "",
                DEFAULT_RUNNER_WORKER_CONNECT_TIMEOUT_SECONDS,
                artifactStorageDir,
                false,
                DEFAULT_ARTIFACT_CLEANUP_RETENTION_HOURS,
                DEFAULT_ARTIFACT_CLEANUP_BATCH_SIZE
        );
    }

    public UiE2eProperties(
            boolean enabled,
            boolean runnerEnabled,
            String runnerMode,
            int defaultTimeoutSeconds,
            int maxTimeoutSeconds,
            int maxScenesPerRun,
            long maxArtifactSizeBytes,
            int maxArtifactCount,
            int maxConcurrency,
            List<String> allowlistBaseUrls,
            boolean captureScreenshotEnabled,
            boolean captureVideoEnabled,
            boolean captureHarEnabled,
            boolean captureTraceEnabled,
            boolean captureJunitXmlEnabled,
            boolean exportEnabled,
            String runnerNodeCommand,
            String runnerNodeModulesDir,
            String runnerWorkerUrl,
            String runnerWorkerCancelUrl,
            String runnerWorkerToken,
            int runnerWorkerConnectTimeoutSeconds,
            String artifactStorageDir
    ) {
        this(
                enabled,
                runnerEnabled,
                runnerMode,
                defaultTimeoutSeconds,
                maxTimeoutSeconds,
                maxScenesPerRun,
                maxArtifactSizeBytes,
                maxArtifactCount,
                maxConcurrency,
                allowlistBaseUrls,
                captureScreenshotEnabled,
                captureVideoEnabled,
                captureHarEnabled,
                captureTraceEnabled,
                captureJunitXmlEnabled,
                exportEnabled,
                runnerNodeCommand,
                runnerNodeModulesDir,
                runnerWorkerUrl,
                runnerWorkerCancelUrl,
                runnerWorkerToken,
                runnerWorkerConnectTimeoutSeconds,
                artifactStorageDir,
                false,
                DEFAULT_ARTIFACT_CLEANUP_RETENTION_HOURS,
                DEFAULT_ARTIFACT_CLEANUP_BATCH_SIZE
        );
    }

    public String effectiveRunnerMode() {
        if (runnerMode == null || runnerMode.isBlank()) {
            return "disabled";
        }
        String normalized = runnerMode.trim().toLowerCase();
        return switch (normalized) {
            case "managed", "http-adapter", "playwright-subprocess", "real-browser" -> normalized;
            default -> "disabled";
        };
    }

    public String effectiveRunnerNodeCommand() {
        return StringUtils.hasText(runnerNodeCommand) ? runnerNodeCommand.trim() : "node";
    }

    public String effectiveRunnerNodeModulesDir() {
        return StringUtils.hasText(runnerNodeModulesDir) ? runnerNodeModulesDir.trim() : "../portal-web/node_modules";
    }

    public String effectiveRunnerWorkerUrl() {
        return sanitizedRunnerWorkerValue(runnerWorkerUrl);
    }

    public String effectiveRunnerWorkerCancelUrl() {
        return sanitizedRunnerWorkerValue(runnerWorkerCancelUrl);
    }

    public String effectiveRunnerWorkerToken() {
        return sanitizedRunnerWorkerValue(runnerWorkerToken);
    }

    public boolean runnerWorkerConfigured() {
        return StringUtils.hasText(effectiveRunnerWorkerUrl());
    }

    public boolean runnerWorkerCancelConfigured() {
        return StringUtils.hasText(effectiveRunnerWorkerCancelUrl());
    }

    public boolean runnerWorkerTokenConfigured() {
        return StringUtils.hasText(effectiveRunnerWorkerToken());
    }

    public int effectiveRunnerWorkerConnectTimeoutSeconds() {
        return boundedPositive(
                runnerWorkerConnectTimeoutSeconds,
                DEFAULT_RUNNER_WORKER_CONNECT_TIMEOUT_SECONDS,
                MAX_RUNNER_WORKER_CONNECT_TIMEOUT_SECONDS
        );
    }

    public String effectiveArtifactStorageDir() {
        if (StringUtils.hasText(artifactStorageDir)) {
            return artifactStorageDir.trim();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "veri-agent", "ui-e2e-artifacts").toString();
    }

    public boolean artifactStorageDirConfigured() {
        return StringUtils.hasText(artifactStorageDir);
    }

    public int effectiveDefaultTimeoutSeconds() {
        return Math.min(
                boundedPositive(defaultTimeoutSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                effectiveMaxTimeoutSeconds()
        );
    }

    public int effectiveMaxTimeoutSeconds() {
        return boundedPositive(maxTimeoutSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
    }

    public int effectiveMaxScenesPerRun() {
        return boundedPositive(maxScenesPerRun, DEFAULT_MAX_SCENES_PER_RUN, MAX_MAX_SCENES_PER_RUN);
    }

    public long effectiveMaxArtifactSizeBytes() {
        return boundedPositiveLong(
                maxArtifactSizeBytes,
                DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
                MAX_ARTIFACT_SIZE_BYTES
        );
    }

    public int effectiveMaxArtifactCount() {
        return boundedPositive(maxArtifactCount, DEFAULT_MAX_ARTIFACT_COUNT, MAX_MAX_ARTIFACT_COUNT);
    }

    public int effectiveMaxConcurrency() {
        return boundedPositive(maxConcurrency, DEFAULT_MAX_CONCURRENCY, MAX_MAX_CONCURRENCY);
    }

    public int effectiveAllowlistHostCount() {
        return (int) allowlistBaseUrls.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .count();
    }

    public int effectiveArtifactCleanupRetentionHours() {
        return boundedPositive(
                artifactCleanupRetentionHours,
                DEFAULT_ARTIFACT_CLEANUP_RETENTION_HOURS,
                MAX_ARTIFACT_CLEANUP_RETENTION_HOURS
        );
    }

    public int effectiveArtifactCleanupBatchSize() {
        return boundedPositive(
                artifactCleanupBatchSize,
                DEFAULT_ARTIFACT_CLEANUP_BATCH_SIZE,
                MAX_ARTIFACT_CLEANUP_BATCH_SIZE
        );
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

    private static String sanitizedRunnerWorkerValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String sanitized = value.trim();
        if (sanitized.contains("\r") || sanitized.contains("\n")) {
            return "";
        }
        return sanitized;
    }
}
