package com.songhg.veri.agent.authorization.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.authorization.infrastructure.mapper.PermissionMapper;
import java.util.List;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class RedisPermissionResolverTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedissonClient redissonClient;
    private PermissionMapper mapper;
    private RedisPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
        redissonClient.getKeys().flushdb();
        mapper = mock(PermissionMapper.class);
        resolver = new RedisPermissionResolver(mapper, redissonClient, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void cachesRolePermissionAggregationInRedis() {
        List<String> roles = List.of("qa-admin", "asset-owner");
        when(mapper.permissionsForRoles(roles)).thenReturn(List.of("asset:read", "asset:manage"));

        assertThat(resolver.permissionsForRoles(roles)).containsExactlyInAnyOrder("asset:read", "asset:manage");
        assertThat(resolver.permissionsForRoles(List.of("asset-owner", "qa-admin")))
                .containsExactlyInAnyOrder("asset:read", "asset:manage");

        verify(mapper).permissionsForRoles(roles);
    }

    @Test
    void cachesScopedPermissionDecisionInRedis() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        AuthUserPrincipal principal = new AuthUserPrincipal(
                userId,
                UUID.randomUUID(),
                "alice",
                "Alice",
                "alice@example.com",
                false,
                1,
                List.of("asset-owner")
        );
        when(mapper.hasPermissionForScope(userId, "asset:manage", "PROJECT", projectId)).thenReturn(true);

        assertThat(resolver.hasPermission(principal, "asset:manage", ResourceScope.project(projectId.toString()))).isTrue();
        assertThat(resolver.hasPermission(principal, "asset:manage", ResourceScope.project(projectId.toString()))).isTrue();

        verify(mapper).hasPermissionForScope(userId, "asset:manage", "PROJECT", projectId);
    }
}
