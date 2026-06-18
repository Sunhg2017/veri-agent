package com.songhg.veri.agent.common.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("db")
@Service
public class AuditRetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionCleanupService.class);

    private final AuditRetentionStore retentionStore;
    private final AuditRetentionProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public AuditRetentionCleanupService(
            AuditRetentionStore retentionStore,
            AuditRetentionProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(retentionStore, properties, meterRegistry, Clock.systemUTC());
    }

    AuditRetentionCleanupService(
            AuditRetentionStore retentionStore,
            AuditRetentionProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.retentionStore = retentionStore;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * Keeps the legacy manual entry point so tests and ad-hoc maintenance can still reuse the feature flag gate.
     */
    public void cleanupByRetentionPolicy() {
        if (!properties.retentionCleanupEnabled()) {
            return;
        }
        cleanupNow();
    }

    public CleanupResult cleanupNow() {
        int retentionDays = properties.effectiveRetentionDays();
        int batchSize = properties.effectiveBatchSize();
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
        try {
            int deleted = retentionStore.cleanupBefore(cutoff, batchSize);
            recordCleanup("success", deleted);
            log.info(
                    "WP1 audit retention cleanup completed cutoff={} retentionDays={} batchSize={} deleted={}",
                    cutoff,
                    retentionDays,
                    batchSize,
                    deleted
            );
            return new CleanupResult(cutoff, retentionDays, batchSize, deleted);
        } catch (RuntimeException ex) {
            recordCleanup("failed", 1);
            log.warn(
                    "WP1 audit retention cleanup failed cutoff={} retentionDays={} batchSize={} error={}",
                    cutoff,
                    retentionDays,
                    batchSize,
                    ex.toString()
            );
            throw ex;
        }
    }

    private void recordCleanup(String result, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("veri.agent.audit.retention.cleanup")
                .description("WP1 audit_log records handled by retention cleanup")
                .tag("result", result)
                .register(meterRegistry)
                .increment(count);
    }

    public record CleanupResult(
            /** 本次清理使用的截止时间 */
            Instant cutoff,
            /** 实际生效的保留天数 */
            int retentionDays,
            /** 本次清理使用的批量大小 */
            int batchSize,
            /** 已删除的审计日志数量 */
            int deleted
    ) {
    }
}
