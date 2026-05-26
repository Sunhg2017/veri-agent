package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.application.port.ProviderConcurrencyLimiter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public class ProcessLocalProviderConcurrencyLimiter implements ProviderConcurrencyLimiter {

    private final Map<UUID, Semaphore> providerConcurrencyLimits = new ConcurrentHashMap<>();

    @Override
    public Optional<Permit> tryAcquire(UUID providerId, int maxPermits) {
        Semaphore semaphore = semaphore(providerId, maxPermits);
        if (!semaphore.tryAcquire()) {
            return Optional.empty();
        }
        return Optional.of(semaphore::release);
    }

    @Override
    public int availablePermits(UUID providerId, int maxPermits) {
        return semaphore(providerId, maxPermits).availablePermits();
    }

    private Semaphore semaphore(UUID providerId, int maxPermits) {
        return providerConcurrencyLimits.computeIfAbsent(
                providerId,
                ignored -> new Semaphore(Math.max(1, maxPermits), true)
        );
    }
}
