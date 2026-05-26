package com.songhg.veri.agent.common.audit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "veri-agent.audit")
public record AuditRetentionProperties(
        /** 是否启用审计日志保留清理任务。 */
        boolean retentionCleanupEnabled,
        /** 审计日志保留天数。 */
        @Min(1) int retentionDays,
        /** 审计日志最小保留天数，防止误配过短清理窗口。 */
        @Min(1) int minRetentionDays,
        /** 单次清理批量上限配置。 */
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
