package com.songhg.veri.agent.uie2e.config;

import java.util.List;
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
        /** Whether trace capture is enabled. */
        @DefaultValue("true") boolean captureTraceEnabled,
        /** Whether run summary export is enabled. */
        @DefaultValue("true") boolean exportEnabled,
        /** Node executable used by the local real-browser runner. */
        @DefaultValue("node") String runnerNodeCommand,
        /** Node modules directory that provides the Playwright runtime. */
        @DefaultValue("../portal-web/node_modules") String runnerNodeModulesDir
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

    @ConstructorBinding
    public UiE2eProperties {
        allowlistBaseUrls = allowlistBaseUrls == null ? List.of() : List.copyOf(allowlistBaseUrls);
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
