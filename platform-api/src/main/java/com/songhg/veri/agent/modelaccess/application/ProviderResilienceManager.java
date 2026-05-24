package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProviderResilienceManager {

    private final ModelAccessProperties properties;
    private final ProviderResilienceStateStore stateStore;
    private final Map<UUID, ProviderCheckCacheEntry> providerCheckCache = new ConcurrentHashMap<>();
    private final Map<UUID, Semaphore> providerConcurrencyLimits = new ConcurrentHashMap<>();

    public ProviderResilienceManager(ModelAccessProperties properties) {
        this(properties, new InMemoryProviderResilienceStateStore());
    }

    @Autowired
    public ProviderResilienceManager(ModelAccessProperties properties, ProviderResilienceStateStore stateStore) {
        this.properties = properties;
        this.stateStore = stateStore;
    }

    public Optional<ProviderCheckResult> cachedProviderCheck(ModelProviderConfig provider) {
        ProviderCheckCacheEntry cached = providerCheckCache.get(provider.id());
        Instant now = Instant.now();
        if (cached == null
                || properties.safeProviderCheckCacheTtlMs() <= 0
                || !cached.expiresAt().isAfter(now)
                || provider.updatedAt().compareTo(cached.providerUpdatedAt()) > 0) {
            return Optional.empty();
        }
        return Optional.of(withCachedFlag(cached.response(), true));
    }

    public void cacheProviderCheck(ModelProviderConfig provider, ProviderCheckResult response) {
        if (properties.safeProviderCheckCacheTtlMs() <= 0) {
            return;
        }
        providerCheckCache.put(provider.id(), new ProviderCheckCacheEntry(
                response,
                provider.updatedAt(),
                Instant.now().plusMillis(properties.safeProviderCheckCacheTtlMs())
        ));
    }

    public boolean isCircuitOpen(ModelProviderConfig provider) {
        return stateStore.circuitState(provider.id())
                .filter(state -> state.openUntil() != null)
                .filter(state -> state.openUntil().isAfter(Instant.now()))
                .isPresent();
    }

    public CircuitStateView circuitState(ModelProviderConfig provider) {
        Optional<ProviderResilienceStateStore.CircuitSnapshot> state = stateStore.circuitState(provider.id());
        if (state.isEmpty()) {
            return new CircuitStateView(false, 0, null);
        }
        ProviderResilienceStateStore.CircuitSnapshot snapshot = state.get();
        Instant now = Instant.now();
        boolean open = snapshot.openUntil() != null && snapshot.openUntil().isAfter(now);
        return new CircuitStateView(open, snapshot.consecutiveFailures(), open ? snapshot.openUntil() : null);
    }

    public int openCircuitCount() {
        return stateStore.openCircuitCount(Instant.now());
    }

    public boolean rateLimitEnabled() {
        return properties.safeProviderRateLimitMaxRequests() > 0;
    }

    public int rateLimitMaxRequests() {
        return properties.safeProviderRateLimitMaxRequests();
    }

    public long rateLimitWindowSeconds() {
        return properties.safeProviderRateLimitWindowSeconds();
    }

    public boolean concurrencyLimitEnabled() {
        return properties.safeProviderMaxConcurrentRequests() > 0;
    }

    public int maxConcurrentRequests() {
        return properties.safeProviderMaxConcurrentRequests();
    }

    public int availablePermits(ModelProviderConfig provider) {
        if (!concurrencyLimitEnabled()) {
            return -1;
        }
        return providerConcurrencyLimits
                .computeIfAbsent(provider.id(), ignored -> new Semaphore(maxConcurrentRequests(), true))
                .availablePermits();
    }

    public void recordProviderSuccess(ModelProviderConfig provider) {
        stateStore.clearCircuit(provider.id());
    }

    public void resetCircuit(ModelProviderConfig provider) {
        stateStore.clearCircuit(provider.id());
    }

    public void recordProviderFailure(ModelProviderConfig provider) {
        int threshold = properties.safeProviderCircuitFailureThreshold();
        long openMs = properties.safeProviderCircuitOpenMs();
        if (openMs <= 0) {
            return;
        }
        stateStore.recordFailure(provider.id(), threshold, openMs, Instant.now());
    }

    public ProviderCallResult callWithRetry(
            ModelProviderClient client,
            ModelProviderConfig provider,
            ProviderCallRequest request
    ) {
        enforceRateLimit(provider);
        Semaphore permit = acquireConcurrencyPermit(provider);
        RuntimeException lastFailure = null;
        try {
            for (int attempt = 0; attempt <= properties.safeProviderMaxRetries(); attempt++) {
                try {
                    return client.call(provider, request);
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                }
            }
            throw lastFailure == null
                    ? new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商调用失败")
                    : lastFailure;
        } finally {
            if (permit != null) {
                permit.release();
            }
        }
    }

    private void enforceRateLimit(ModelProviderConfig provider) {
        int limit = rateLimitMaxRequests();
        if (limit <= 0) {
            return;
        }
        long windowSeconds = rateLimitWindowSeconds();
        ProviderResilienceStateStore.RateLimitSnapshot updated =
                stateStore.incrementRateLimit(provider.id(), windowSeconds, Instant.now());
        if (updated.count() > limit) {
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED,
                    "模型供应商请求超过限流阈值: " + limit + "/" + windowSeconds + "s");
        }
    }

    private Semaphore acquireConcurrencyPermit(ModelProviderConfig provider) {
        int maxConcurrentRequests = maxConcurrentRequests();
        if (maxConcurrentRequests <= 0) {
            return null;
        }
        Semaphore semaphore = providerConcurrencyLimits.computeIfAbsent(
                provider.id(),
                ignored -> new Semaphore(maxConcurrentRequests, true)
        );
        if (!semaphore.tryAcquire()) {
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED,
                    "模型供应商并发处理已达到上限: " + maxConcurrentRequests);
        }
        return semaphore;
    }

    private ProviderCheckResult withCachedFlag(ProviderCheckResult response, boolean cached) {
        return new ProviderCheckResult(
                response.providerId(),
                response.providerName(),
                response.providerType(),
                response.providerStatus(),
                response.status(),
                response.latencyMs(),
                response.modelName(),
                response.errorCode(),
                response.errorMessage(),
                cached,
                response.checkedAt()
        );
    }

    private record ProviderCheckCacheEntry(
            ProviderCheckResult response,
            Instant providerUpdatedAt,
            Instant expiresAt
    ) {
    }

    public record CircuitStateView(boolean open, int consecutiveFailures, Instant openUntil) {
    }
}
