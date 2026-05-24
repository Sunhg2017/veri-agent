package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore.CircuitSnapshot;
import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore.RateLimitSnapshot;
import com.songhg.veri.agent.modelaccess.application.port.ProviderResilienceStateStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;



@Testcontainers(disabledWithoutDocker = true)
class RedisProviderResilienceStateStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisProviderResilienceStateStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        store = new RedisProviderResilienceStateStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
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
        RedisProviderResilienceStateStore anotherStore = new RedisProviderResilienceStateStore(redisTemplate);

        RateLimitSnapshot first = store.incrementRateLimit(providerId, 60, now);
        RateLimitSnapshot second = anotherStore.incrementRateLimit(providerId, 60, now.plusMillis(1));

        assertThat(first.count()).isEqualTo(1);
        assertThat(second.window()).isEqualTo(first.window());
        assertThat(second.count()).isEqualTo(2);
    }
}
