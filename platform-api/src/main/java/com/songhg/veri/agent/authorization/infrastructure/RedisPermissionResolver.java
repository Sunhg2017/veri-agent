package com.songhg.veri.agent.authorization.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionResolver;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.authorization.infrastructure.mapper.PermissionMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Profile("db & redis")
@Component
public class RedisPermissionResolver implements PermissionResolver {

    private static final String KEY_PREFIX = "veri-agent:authorization:permissions:";
    private static final String ROLE_PERMISSIONS_KEY = KEY_PREFIX + "roles";
    private static final String SCOPE_PERMISSION_KEY = KEY_PREFIX + "scope-decisions";
    private static final long ROLE_PERMISSION_TTL_SECONDS = 60;
    private static final long SCOPE_PERMISSION_TTL_SECONDS = 30;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PermissionMapper mapper;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public RedisPermissionResolver(PermissionMapper mapper, RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<String> permissionsForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        String key = roleKey(roles);
        String cached = rolePermissions().get(key);
        if (cached != null) {
            return Set.copyOf(new TreeSet<>(stringList(cached)));
        }
        Set<String> permissions = Set.copyOf(new TreeSet<>(mapper.permissionsForRoles(roles)));
        rolePermissions().fastPut(key, json(permissions), ROLE_PERMISSION_TTL_SECONDS, TimeUnit.SECONDS);
        return permissions;
    }

    @Override
    public boolean hasPermission(AuthUserPrincipal principal, String permission, ResourceScope scope) {
        if (principal == null || !StringUtils.hasText(permission)) {
            return false;
        }
        ResourceScope resourceScope = scope == null ? ResourceScope.platform() : scope;
        UUID scopeId = null;
        if (!resourceScope.isPlatform()) {
            Optional<UUID> parsedScopeId = uuid(resourceScope.scopeId());
            if (parsedScopeId.isEmpty()) {
                return false;
            }
            scopeId = parsedScopeId.get();
        }
        String key = scopeKey(principal.userId(), permission, resourceScope, scopeId);
        String cached = scopePermissions().get(key);
        if (cached != null) {
            return Boolean.parseBoolean(cached);
        }
        boolean allowed = mapper.hasPermissionForScope(
                principal.userId(),
                permission.trim(),
                resourceScope.scopeType(),
                scopeId
        );
        // Scope decisions depend on role bindings and expiry; keep TTL short to bound stale grants.
        scopePermissions().fastPut(key, Boolean.toString(allowed), SCOPE_PERMISSION_TTL_SECONDS, TimeUnit.SECONDS);
        return allowed;
    }

    private RMapCache<String, String> rolePermissions() {
        return redissonClient.getMapCache(ROLE_PERMISSIONS_KEY);
    }

    private RMapCache<String, String> scopePermissions() {
        return redissonClient.getMapCache(SCOPE_PERMISSION_KEY);
    }

    private String roleKey(List<String> roles) {
        return String.join(",", roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .sorted()
                .toList());
    }

    private String scopeKey(UUID userId, String permission, ResourceScope scope, UUID scopeId) {
        return userId + "|" + permission.trim() + "|" + scope.scopeType() + "|" + (scopeId == null ? "" : scopeId);
    }

    private Optional<UUID> uuid(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String json(Set<String> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Redis 权限缓存无法序列化");
        }
    }

    private List<String> stringList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Redis 权限缓存无法解析");
        }
    }
}
