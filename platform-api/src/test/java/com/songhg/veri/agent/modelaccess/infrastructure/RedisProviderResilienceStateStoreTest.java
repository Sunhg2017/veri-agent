package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore.CircuitSnapshot;
import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore.RateLimitSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;



@Testcontainers(disabledWithoutDocker = true)
class RedisProviderResilienceStateStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedissonClient redissonClient;
    private RedisProviderResilienceStateStore store;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
        redissonClient.getKeys().flushdb();
        store = new RedisProviderResilienceStateStore(redissonClient);
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void storesCircuitStateAndOpenCircuitIndexInRedis() {
        UUID providerId = UUID.randomUUID();
        Instant now = Instant.now();

        CircuitSnapshot first = store.recordFailure(providerId, 2, 60_000, now);
        CircuitSnapshot second = store.recordFailure(providerId, 2, 60_000, now.plusMillis(1));

        assertThat(first.consecutiveFailures()).isEqualTo(1);
        assertThat(first.openUntil()).isNull();
        assertThat(second.consecutiveFailures()).isEqualTo(2);
        assertThat(second.openUntil()).isAfter(now);
        assertThat(store.circuitState(providerId)).contains(second);
        assertThat(store.openCircuitCount(now)).isEqualTo(1);

        store.clearCircuit(providerId);

        assertThat(store.circuitState(providerId)).isEmpty();
        assertThat(store.openCircuitCount(now)).isZero();
    }

    @Test
    void incrementsRateLimitWindowInRedisAcrossStoreInstances() {
        UUID providerId = UUID.randomUUID();
        Instant now = Instant.now();
        RedisProviderResilienceStateStore anotherStore = new RedisProviderResilienceStateStore(redissonClient);

        RateLimitSnapshot first = store.incrementRateLimit(providerId, 60, now);
        RateLimitSnapshot second = anotherStore.incrementRateLimit(providerId, 60, now.plusMillis(1));

        assertThat(first.count()).isEqualTo(1);
        assertThat(second.window()).isEqualTo(first.window());
        assertThat(second.count()).isEqualTo(2);
    }

    @Test
    void limitsProviderConcurrencyAcrossRedissonLimiterInstances() {
        UUID providerId = UUID.randomUUID();
        RedissonProviderConcurrencyLimiter firstNode = new RedissonProviderConcurrencyLimiter(redissonClient);
        RedissonProviderConcurrencyLimiter secondNode = new RedissonProviderConcurrencyLimiter(redissonClient);

        var firstPermit = firstNode.tryAcquire(providerId, 1);
        var secondPermit = secondNode.tryAcquire(providerId, 1);

        assertThat(firstPermit).isPresent();
        assertThat(secondPermit).isEmpty();

        firstPermit.orElseThrow().release();

        assertThat(secondNode.tryAcquire(providerId, 1)).isPresent();
    }
}
