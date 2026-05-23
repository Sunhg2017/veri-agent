package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.ProviderResilienceStateStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedisProviderResilienceStateStore implements ProviderResilienceStateStore {

    private static final String KEY_PREFIX = "veri-agent:model-access:provider-resilience:";
    private static final String CIRCUIT_KEY_PREFIX = KEY_PREFIX + "circuit:";
    private static final String RATE_KEY_PREFIX = KEY_PREFIX + "rate:";
    private static final String OPEN_CIRCUIT_PROVIDERS_KEY = KEY_PREFIX + "open-circuit-providers";
    private static final String FIELD_FAILURES = "failures";
    private static final String FIELD_OPEN_UNTIL = "openUntil";

    private static final DefaultRedisScript<List> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local indexKey = KEYS[2]
            local now = tonumber(ARGV[1])
            local threshold = tonumber(ARGV[2])
            local openMs = tonumber(ARGV[3])
            local providerId = ARGV[4]
            local failures = tonumber(redis.call('HGET', key, 'failures') or '0')
            local openUntil = tonumber(redis.call('HGET', key, 'openUntil') or '0')
            if openUntil > 0 and openUntil < now then
              failures = 0
              openUntil = 0
            end
            failures = failures + 1
            if failures >= threshold then
              openUntil = now + openMs
              redis.call('SADD', indexKey, providerId)
            else
              openUntil = 0
              redis.call('SREM', indexKey, providerId)
            end
            redis.call('HSET', key, 'failures', failures, 'openUntil', openUntil)
            local ttl = openUntil > now and (openUntil - now) or openMs
            if ttl <= 0 then
              ttl = 60000
            end
            redis.call('PEXPIRE', key, ttl)
            return {failures, openUntil}
            """, List.class);

    private static final DefaultRedisScript<Long> INCREMENT_RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]) + 1)
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisProviderResilienceStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<CircuitSnapshot> circuitState(UUID providerId) {
        String key = circuitKey(providerId);
        Object failures = redisTemplate.opsForHash().get(key, FIELD_FAILURES);
        Object openUntil = redisTemplate.opsForHash().get(key, FIELD_OPEN_UNTIL);
        if (failures == null && openUntil == null) {
            return Optional.empty();
        }
        CircuitSnapshot snapshot = new CircuitSnapshot(
                parseInt(failures),
                instantFromEpochMillis(parseLong(openUntil))
        );
        if (snapshot.openUntil() != null && !snapshot.openUntil().isAfter(Instant.now())) {
            redisTemplate.opsForSet().remove(OPEN_CIRCUIT_PROVIDERS_KEY, providerId.toString());
        }
        return Optional.of(snapshot);
    }

    @Override
    public void clearCircuit(UUID providerId) {
        redisTemplate.delete(circuitKey(providerId));
        redisTemplate.opsForSet().remove(OPEN_CIRCUIT_PROVIDERS_KEY, providerId.toString());
    }

    @Override
    public CircuitSnapshot recordFailure(UUID providerId, int threshold, long openMs, Instant now) {
        List<?> result = redisTemplate.execute(
                RECORD_FAILURE_SCRIPT,
                List.of(circuitKey(providerId), OPEN_CIRCUIT_PROVIDERS_KEY),
                Long.toString(now.toEpochMilli()),
                Integer.toString(threshold),
                Long.toString(openMs),
                providerId.toString()
        );
        if (result == null || result.size() < 2) {
            return new CircuitSnapshot(1, null);
        }
        return new CircuitSnapshot(
                parseInt(result.get(0)),
                instantFromEpochMillis(parseLong(result.get(1)))
        );
    }

    @Override
    public int openCircuitCount(Instant now) {
        Set<String> providerIds = redisTemplate.opsForSet().members(OPEN_CIRCUIT_PROVIDERS_KEY);
        if (providerIds == null || providerIds.isEmpty()) {
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
                redisTemplate.opsForSet().remove(OPEN_CIRCUIT_PROVIDERS_KEY, providerId);
            }
        }
        return count;
    }

    @Override
    public RateLimitSnapshot incrementRateLimit(UUID providerId, long windowSeconds, Instant now) {
        long currentWindow = now.getEpochSecond() / windowSeconds;
        Long count = redisTemplate.execute(
                INCREMENT_RATE_LIMIT_SCRIPT,
                List.of(rateKey(providerId, currentWindow)),
                Long.toString(windowSeconds)
        );
        return new RateLimitSnapshot(currentWindow, count == null ? 1 : count);
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
