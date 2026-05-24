package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.audit.mapper.AuditMapper;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcAuditRetentionStore implements AuditRetentionStore {

    private final AuditMapper mapper;

    public JdbcAuditRetentionStore(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int cleanupBefore(Instant cutoff, int batchSize) {
        return mapper.cleanupAuditLogBefore(cutoff, batchSize);
    }
}
