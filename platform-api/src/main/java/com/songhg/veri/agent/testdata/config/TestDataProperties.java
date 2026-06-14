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
        /** Allows redacted summary export. */
        @DefaultValue("true") boolean exportEnabled
) {
    private static final int DEFAULT_RECORD_MAX_COUNT = 10_000;
    private static final int MAX_RECORD_MAX_COUNT = 1_000_000;
    private static final int DEFAULT_RECORD_SUMMARY_MAX_BYTES = 2_048;
    private static final int MAX_RECORD_SUMMARY_MAX_BYTES = 65_536;
    private static final int DEFAULT_LEASE_TTL_SECONDS = 1_800;
    private static final int MAX_LEASE_TTL_SECONDS = 86_400;

    @ConstructorBinding
    public TestDataProperties {
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

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
