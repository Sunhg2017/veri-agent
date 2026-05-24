package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public class ProcessLocalProviderResilienceStateStore implements ProviderResilienceStateStore {

    private final Map<UUID, CircuitSnapshot> circuitStates = new ConcurrentHashMap<>();
    private final Map<UUID, RateLimitSnapshot> rateWindows = new ConcurrentHashMap<>();

    @Override
    public Optional<CircuitSnapshot> circuitState(UUID providerId) {
        return Optional.ofNullable(circuitStates.get(providerId));
    }

    @Override
    public void clearCircuit(UUID providerId) {
        circuitStates.remove(providerId);
    }

    @Override
    public CircuitSnapshot recordFailure(UUID providerId, int threshold, long openMs, Instant now) {
        return circuitStates.compute(providerId, (id, existing) -> {
            boolean expiredOpenCircuit = existing != null
                    && existing.openUntil() != null
                    && existing.openUntil().isBefore(now);
            int failures = existing == null || expiredOpenCircuit ? 1 : existing.consecutiveFailures() + 1;
            Instant openUntil = failures >= threshold ? now.plusMillis(openMs) : null;
            return new CircuitSnapshot(failures, openUntil);
        });
    }

    @Override
    public int openCircuitCount(Instant now) {
        return (int) circuitStates.values()
                .stream()
                .filter(state -> state.openUntil() != null && state.openUntil().isAfter(now))
                .count();
    }

    @Override
    public RateLimitSnapshot incrementRateLimit(UUID providerId, long windowSeconds, Instant now) {
        long currentWindow = now.getEpochSecond() / windowSeconds;
        return rateWindows.compute(providerId, (id, existing) -> {
            if (existing == null || existing.window() != currentWindow) {
                return new RateLimitSnapshot(currentWindow, 1);
            }
            return new RateLimitSnapshot(currentWindow, existing.count() + 1);
        });
    }
}
