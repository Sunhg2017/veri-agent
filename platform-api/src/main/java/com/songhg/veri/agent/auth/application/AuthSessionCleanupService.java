package com.songhg.veri.agent.auth.application;

import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionCleanupService {

    private final AuthSessionStore sessionStore;
    private final AuthProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Autowired
    public AuthSessionCleanupService(
            AuthSessionStore sessionStore,
            AuthProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(sessionStore, properties, meterRegistry, Clock.systemUTC());
    }

    AuthSessionCleanupService(
            AuthSessionStore sessionStore,
            AuthProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Keeps the legacy manual entry point so tests and ad-hoc maintenance can still reuse the feature flag gate.
     */
    public void cleanupExpiredSessions() {
        if (!properties.sessionCleanupEnabled()) {
            return;
        }
        cleanupNow();
    }

    public int cleanupNow() {
        Instant now = Instant.now(clock);
        Instant expiresBefore = now.minusSeconds(retentionSeconds());
        int deleted = sessionStore.cleanupExpiredSessions(expiresBefore, expiresBefore);
        if (deleted > 0) {
            Counter.builder("veri.agent.auth.session.cleanup")
                    .description("WP1 expired or revoked auth sessions removed by cleanup")
                    .register(meterRegistry)
                    .increment(deleted);
        }
        return deleted;
    }

    private long retentionSeconds() {
        return Math.max(1, properties.sessionCleanupRetentionSeconds());
    }
}
