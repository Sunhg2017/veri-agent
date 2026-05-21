package com.songhg.veri.agent.common.audit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "veri-agent.audit")
public record AuditRetentionProperties(
        boolean retentionCleanupEnabled,
        @Min(1) int retentionDays,
        @Min(1) int minRetentionDays,
        @Min(1) int retentionCleanupBatchSize
) {

    static final int MAX_BATCH_SIZE = 10_000;

    int effectiveRetentionDays() {
        return Math.max(Math.max(1, retentionDays), Math.max(1, minRetentionDays));
    }

    int effectiveBatchSize() {
        return Math.max(1, Math.min(retentionCleanupBatchSize, MAX_BATCH_SIZE));
    }
}
