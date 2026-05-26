package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.port.ProviderConcurrencyLimiter;
import java.util.Optional;
import java.util.UUID;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedissonProviderConcurrencyLimiter implements ProviderConcurrencyLimiter {

    private static final String KEY_PREFIX = "veri-agent:model-access:provider-concurrency:";

    private final RedissonClient redissonClient;

    public RedissonProviderConcurrencyLimiter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Optional<Permit> tryAcquire(UUID providerId, int maxPermits) {
        RSemaphore semaphore = semaphore(providerId, maxPermits);
        if (!semaphore.tryAcquire()) {
            return Optional.empty();
        }
        return Optional.of(semaphore::release);
    }

    @Override
    public int availablePermits(UUID providerId, int maxPermits) {
        return semaphore(providerId, maxPermits).availablePermits();
    }

    private RSemaphore semaphore(UUID providerId, int maxPermits) {
        RSemaphore semaphore = redissonClient.getSemaphore(KEY_PREFIX + providerId);
        semaphore.trySetPermits(Math.max(1, maxPermits));
        return semaphore;
    }
}
