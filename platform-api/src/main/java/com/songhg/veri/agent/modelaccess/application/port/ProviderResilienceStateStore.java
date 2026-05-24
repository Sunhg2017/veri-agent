package com.songhg.veri.agent.modelaccess.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProviderResilienceStateStore {

    Optional<CircuitSnapshot> circuitState(UUID providerId);

    void clearCircuit(UUID providerId);

    CircuitSnapshot recordFailure(UUID providerId, int threshold, long openMs, Instant now);

    int openCircuitCount(Instant now);

    RateLimitSnapshot incrementRateLimit(UUID providerId, long windowSeconds, Instant now);

    record CircuitSnapshot(int consecutiveFailures, Instant openUntil) {
    }

    record RateLimitSnapshot(long window, long count) {
    }
}
