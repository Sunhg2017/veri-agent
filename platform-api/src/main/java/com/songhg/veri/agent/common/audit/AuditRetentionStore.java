package com.songhg.veri.agent.common.audit;

import java.time.Instant;

public interface AuditRetentionStore {

    int cleanupBefore(Instant cutoff, int batchSize);
}
