package com.songhg.veri.agent.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Centralized XXL-JOB executor settings for all platform-managed background jobs.
 */
@ConfigurationProperties(prefix = "veri-agent.xxl-job")
public record XxlJobProperties(
        /** Enables XXL-JOB executor bootstrap and handler registration. */
        @DefaultValue("false") boolean enabled,
        /** Scheduler admin endpoints used for registry and callback delivery. */
        String adminAddresses,
        /** Shared access token configured between admin and executor. */
        String accessToken,
        /** HTTP timeout for admin registry/callback requests, in seconds. */
        @DefaultValue("3") int requestTimeoutSeconds,
        /** Embedded executor server settings exposed to XXL-JOB admin. */
        Executor executor
) {

    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 3;
    private static final int MAX_REQUEST_TIMEOUT_SECONDS = 10;

    public XxlJobProperties {
        executor = executor == null ? new Executor(null, null, null, 0, null, 30) : executor;
    }

    public int effectiveRequestTimeoutSeconds() {
        return boundedPositive(requestTimeoutSeconds, DEFAULT_REQUEST_TIMEOUT_SECONDS, MAX_REQUEST_TIMEOUT_SECONDS);
    }

    public String effectiveAdminAddresses() {
        return boundedText(adminAddresses, null, 2048);
    }

    public String effectiveAccessToken() {
        return boundedText(accessToken, null, 512);
    }

    public record Executor(
            /** Executor app name shown in XXL-JOB admin. */
            String appname,
            /** Optional public address override for reverse proxy scenarios. */
            String address,
            /** Optional IP override for embedded executor server binding/registry. */
            String ip,
            /** Optional embedded executor port; non-positive means auto-allocate. */
            @DefaultValue("0") int port,
            /** Optional job log output directory. */
            String logPath,
            /** Retention days for local XXL-JOB handler logs. */
            @DefaultValue("30") int logRetentionDays
    ) {
        private static final String DEFAULT_APPNAME = "platform-api";
        private static final int DEFAULT_LOG_RETENTION_DAYS = 30;
        private static final int MAX_LOG_RETENTION_DAYS = 3650;

        public String effectiveAppname() {
            return boundedText(appname, DEFAULT_APPNAME, 128);
        }

        public String effectiveAddress() {
            return boundedText(address, null, 512);
        }

        public String effectiveIp() {
            return boundedText(ip, null, 128);
        }

        public int effectivePort() {
            return Math.max(0, port);
        }

        public String effectiveLogPath() {
            return boundedText(logPath, null, 1024);
        }

        public int effectiveLogRetentionDays() {
            return boundedPositive(logRetentionDays, DEFAULT_LOG_RETENTION_DAYS, MAX_LOG_RETENTION_DAYS);
        }
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
