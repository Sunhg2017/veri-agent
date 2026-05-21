package com.songhg.veri.agent.common.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditRetentionCleanupServiceTest {

    @Test
    void cleanupUsesRetentionFloorAndBatchLimit() {
        CountingStore store = new CountingStore(3);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Instant now = Instant.parse("2026-05-20T00:00:00Z");
        AuditRetentionCleanupService cleanupService = new AuditRetentionCleanupService(
                store,
                new AuditRetentionProperties(true, 7, 30, 50_000),
                meterRegistry,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        AuditRetentionCleanupService.CleanupResult result = cleanupService.cleanupNow();

        assertThat(result.retentionDays()).isEqualTo(30);
        assertThat(result.batchSize()).isEqualTo(10_000);
        assertThat(result.cutoff()).isEqualTo(Instant.parse("2026-04-20T00:00:00Z"));
        assertThat(result.deleted()).isEqualTo(3);
        assertThat(store.cutoff).isEqualTo(result.cutoff());
        assertThat(store.batchSize).isEqualTo(10_000);
        assertThat(meterRegistry.get("veri.agent.audit.retention.cleanup")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(3.0);
    }

    @Test
    void scheduledCleanupHonorsFeatureFlag() {
        CountingStore store = new CountingStore(1);
        AuditRetentionCleanupService cleanupService = new AuditRetentionCleanupService(
                store,
                new AuditRetentionProperties(false, 365, 30, 1000),
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC)
        );

        cleanupService.cleanupByRetentionPolicy();

        assertThat(store.calls).isZero();
    }

    @Test
    void cleanupRecordsFailureMetricAndRethrows() {
        CountingStore store = new CountingStore(0);
        store.failure = new IllegalStateException("cleanup failed");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuditRetentionCleanupService cleanupService = new AuditRetentionCleanupService(
                store,
                new AuditRetentionProperties(true, 365, 30, 1000),
                meterRegistry,
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(cleanupService::cleanupNow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup failed");
        assertThat(meterRegistry.get("veri.agent.audit.retention.cleanup")
                .tag("result", "failed")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private static class CountingStore implements AuditRetentionStore {

        private final int deleted;
        private int calls;
        private Instant cutoff;
        private int batchSize;
        private RuntimeException failure;

        CountingStore(int deleted) {
            this.deleted = deleted;
        }

        @Override
        public int cleanupBefore(Instant cutoff, int batchSize) {
            calls++;
            this.cutoff = cutoff;
            this.batchSize = batchSize;
            if (failure != null) {
                throw failure;
            }
            return deleted;
        }
    }
}
