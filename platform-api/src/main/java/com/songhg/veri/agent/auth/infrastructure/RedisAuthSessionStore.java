package com.songhg.veri.agent.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import com.songhg.veri.agent.auth.infrastructure.mapper.AuthMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db & redis")
@Repository
public class RedisAuthSessionStore implements AuthSessionStore {

    private static final String KEY_PREFIX = "veri-agent:auth:sessions:";
    private static final String SESSION_KEY = KEY_PREFIX + "by-id";
    private static final String REFRESH_TOKEN_KEY = KEY_PREFIX + "by-refresh-token";
    private static final long MAX_ACTIVE_CACHE_TTL_MS = 30_000L;

    private final AuthMapper mapper;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public RedisAuthSessionStore(AuthMapper mapper, RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void create(AuthSessionDraft draft) {
        mapper.createSession(draft);
        cache(new AuthSessionRecord(
                draft.sessionId(),
                draft.userId(),
                draft.refreshTokenHash(),
                draft.authVersion(),
                draft.expiresAt(),
                false
        ));
    }

    @Override
    public boolean isActive(UUID sessionId, UUID userId, long authVersion, Instant now) {
        AuthSessionRecord cached = cachedBySessionId(sessionId).orElse(null);
        if (cached == null) {
            if (!mapper.isSessionActive(sessionId, userId, authVersion, now)) {
                return false;
            }
            cached = Optional.ofNullable(mapper.findSessionById(sessionId))
                    .map(this::cache)
                    .orElse(null);
        }
        if (cached == null || !cached.userId().equals(userId) || cached.authVersion() != authVersion) {
            return false;
        }
        return cached.activeAt(now);
    }

    @Override
    public Optional<AuthSessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
        String sessionId = refreshTokenIndex().get(refreshTokenHash);
        if (sessionId != null) {
            Optional<AuthSessionRecord> cached = cachedBySessionId(UUID.fromString(sessionId));
            if (cached.isPresent()) {
                return cached;
            }
        }
        return Optional.ofNullable(mapper.findByRefreshTokenHash(refreshTokenHash))
                .map(this::cache);
    }

    @Override
    public void revoke(UUID sessionId, UUID revokedBy, String reason) {
        mapper.revokeSession(sessionId, revokedBy, reason);
        cachedBySessionId(sessionId)
                .map(session -> new AuthSessionRecord(
                        session.sessionId(),
                        session.userId(),
                        session.refreshTokenHash(),
                        session.authVersion(),
                        session.expiresAt(),
                        true
                ))
                .ifPresent(this::cache);
    }

    @Override
    public int cleanupExpiredSessions(Instant expiresBefore, Instant revokedBefore) {
        return mapper.cleanupSessions(expiresBefore, revokedBefore);
    }

    private AuthSessionRecord cache(AuthSessionRecord record) {
        long ttlMs = cacheTtlMs(record.expiresAt());
        if (ttlMs <= 0) {
            sessionCache().fastRemove(record.sessionId().toString());
            refreshTokenIndex().fastRemove(record.refreshTokenHash());
            return record;
        }
        sessionCache().fastPut(record.sessionId().toString(), json(record), ttlMs, TimeUnit.MILLISECONDS);
        refreshTokenIndex().fastPut(record.refreshTokenHash(), record.sessionId().toString(), ttlMs, TimeUnit.MILLISECONDS);
        return record;
    }

    private Optional<AuthSessionRecord> cachedBySessionId(UUID sessionId) {
        String json = sessionCache().get(sessionId.toString());
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AuthSessionRecord.class));
        } catch (JsonProcessingException exception) {
            sessionCache().fastRemove(sessionId.toString());
            throw new BusinessException(ErrorCode.INVALID_STATE, "Redis 会话缓存无法解析");
        }
    }

    private RMapCache<String, String> sessionCache() {
        return redissonClient.getMapCache(SESSION_KEY);
    }

    private RMapCache<String, String> refreshTokenIndex() {
        return redissonClient.getMapCache(REFRESH_TOKEN_KEY);
    }

    private String json(AuthSessionRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Redis 会话缓存无法序列化");
        }
    }

    private long cacheTtlMs(Instant expiresAt) {
        if (expiresAt == null) {
            return 0;
        }
        long untilSessionExpiry = Duration.between(Instant.now(), expiresAt).toMillis();
        if (untilSessionExpiry <= 0) {
            return 0;
        }
        // Active sessions are cached briefly because DB user status/authVersion changes must converge quickly.
        return Math.min(untilSessionExpiry, MAX_ACTIVE_CACHE_TTL_MS);
    }
}
