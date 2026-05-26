package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedisProviderResilienceStateStore implements ProviderResilienceStateStore {

    private static final String KEY_PREFIX = "veri-agent:model-access:provider-resilience:";
    private static final String CIRCUIT_KEY_PREFIX = KEY_PREFIX + "circuit:";
    private static final String RATE_KEY_PREFIX = KEY_PREFIX + "rate:";
    private static final String OPEN_CIRCUIT_PROVIDERS_KEY = KEY_PREFIX + "open-circuit-providers";
    private static final String CIRCUIT_LOCK_KEY_PREFIX = KEY_PREFIX + "circuit-lock:";
    private static final String FIELD_FAILURES = "failures";
    private static final String FIELD_OPEN_UNTIL = "openUntil";
    private static final long DEFAULT_CIRCUIT_TTL_MS = 60_000L;

    private final RedissonClient redissonClient;

    public RedisProviderResilienceStateStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Optional<CircuitSnapshot> circuitState(UUID providerId) {
        RMapCache<String, String> circuit = circuit(providerId);
        Object failures = circuit.get(FIELD_FAILURES);
        Object openUntil = circuit.get(FIELD_OPEN_UNTIL);
        if (failures == null && openUntil == null) {
            return Optional.empty();
        }
        CircuitSnapshot snapshot = new CircuitSnapshot(
                parseInt(failures),
                instantFromEpochMillis(parseLong(openUntil))
        );
        if (snapshot.openUntil() != null && !snapshot.openUntil().isAfter(Instant.now())) {
            openCircuitProviders().remove(providerId.toString());
        }
        return Optional.of(snapshot);
    }

    @Override
    public void clearCircuit(UUID providerId) {
        circuit(providerId).delete();
        openCircuitProviders().remove(providerId.toString());
    }

    @Override
    public CircuitSnapshot recordFailure(UUID providerId, int threshold, long openMs, Instant now) {
        RLock lock = redissonClient.getLock(CIRCUIT_LOCK_KEY_PREFIX + providerId);
        lock.lock();
        try {
            RMapCache<String, String> circuit = circuit(providerId);
            long nowMs = now.toEpochMilli();
            int failures = parseInt(circuit.get(FIELD_FAILURES));
            long openUntilMs = parseLong(circuit.get(FIELD_OPEN_UNTIL));
            if (openUntilMs > 0 && openUntilMs < nowMs) {
                failures = 0;
                openUntilMs = 0;
            }
            failures++;
            if (failures >= threshold) {
                openUntilMs = nowMs + openMs;
                openCircuitProviders().add(providerId.toString());
            } else {
                openUntilMs = 0;
                openCircuitProviders().remove(providerId.toString());
            }
            long ttlMs = openUntilMs > nowMs ? openUntilMs - nowMs : Math.max(openMs, DEFAULT_CIRCUIT_TTL_MS);
            circuit.fastPut(FIELD_FAILURES, Integer.toString(failures), ttlMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuit.fastPut(FIELD_OPEN_UNTIL, Long.toString(openUntilMs), ttlMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return new CircuitSnapshot(failures, instantFromEpochMillis(openUntilMs));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int openCircuitCount(Instant now) {
        Set<String> providerIds = openCircuitProviders().readAll();
        if (providerIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String providerId : providerIds) {
            try {
                Optional<CircuitSnapshot> snapshot = circuitState(UUID.fromString(providerId));
                if (snapshot.isPresent()
                        && snapshot.get().openUntil() != null
                        && snapshot.get().openUntil().isAfter(now)) {
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
                openCircuitProviders().remove(providerId);
            }
        }
        return count;
    }

    @Override
    public RateLimitSnapshot incrementRateLimit(UUID providerId, long windowSeconds, Instant now) {
        long currentWindow = now.getEpochSecond() / windowSeconds;
        RAtomicLong counter = redissonClient.getAtomicLong(rateKey(providerId, currentWindow));
        long count = counter.incrementAndGet();
        if (count == 1) {
            counter.expire(Duration.ofSeconds(windowSeconds + 1));
        }
        return new RateLimitSnapshot(currentWindow, count);
    }

    private RMapCache<String, String> circuit(UUID providerId) {
        return redissonClient.getMapCache(circuitKey(providerId));
    }

    private RSet<String> openCircuitProviders() {
        return redissonClient.getSet(OPEN_CIRCUIT_PROVIDERS_KEY);
    }

    private String circuitKey(UUID providerId) {
        return CIRCUIT_KEY_PREFIX + providerId;
    }

    private String rateKey(UUID providerId, long window) {
        return RATE_KEY_PREFIX + providerId + ":" + window;
    }

    private int parseInt(Object value) {
        long parsed = parseLong(value);
        return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Instant instantFromEpochMillis(long value) {
        return value <= 0 ? null : Instant.ofEpochMilli(value);
    }
}
