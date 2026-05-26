package com.songhg.veri.agent.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.infrastructure.mapper.AuthMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuthSessionStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedissonClient redissonClient;
    private AuthMapper mapper;
    private RedisAuthSessionStore store;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
        redissonClient.getKeys().flushdb();
        mapper = mock(AuthMapper.class);
        store = new RedisAuthSessionStore(mapper, redissonClient, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void cachesCreatedSessionAndRevocationInRedis() {
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        AuthSessionDraft draft = new AuthSessionDraft(
                sessionId,
                userId,
                "access-hash",
                "refresh-hash",
                3,
                Instant.now().plusSeconds(300)
        );

        store.create(draft);

        assertThat(store.isActive(sessionId, userId, 3, Instant.now())).isTrue();
        assertThat(store.findByRefreshTokenHash("refresh-hash"))
                .hasValueSatisfying(record -> assertThat(record.sessionId()).isEqualTo(sessionId));
        verify(mapper).createSession(draft);
        verify(mapper, never()).findSessionById(sessionId);

        store.revoke(sessionId, userId, "logout");

        assertThat(store.isActive(sessionId, userId, 3, Instant.now())).isFalse();
        assertThat(store.findByRefreshTokenHash("refresh-hash"))
                .hasValueSatisfying(record -> assertThat(record.revoked()).isTrue());
    }

    @Test
    void loadsDbSessionOnceOnCacheMiss() {
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        AuthSessionRecord record = new AuthSessionRecord(
                sessionId,
                userId,
                "refresh-hash-2",
                7,
                Instant.now().plusSeconds(300),
                false
        );
        when(mapper.isSessionActive(sessionId, userId, 7, Instant.EPOCH)).thenReturn(true);
        when(mapper.findSessionById(sessionId)).thenReturn(record);

        assertThat(store.isActive(sessionId, userId, 7, Instant.EPOCH)).isTrue();
        assertThat(store.isActive(sessionId, userId, 7, Instant.now())).isTrue();

        verify(mapper).isSessionActive(sessionId, userId, 7, Instant.EPOCH);
        verify(mapper).findSessionById(sessionId);
    }
}
