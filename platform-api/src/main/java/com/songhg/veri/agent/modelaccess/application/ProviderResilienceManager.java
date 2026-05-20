package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
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
import org.springframework.stereotype.Component;

@Component
public class ProviderResilienceManager {

    private final ModelAccessProperties properties;
    private final Map<UUID, ProviderCircuitState> providerCircuitStates = new ConcurrentHashMap<>();
    private final Map<UUID, ProviderCheckCacheEntry> providerCheckCache = new ConcurrentHashMap<>();
    private final Map<UUID, RateWindow> providerRateWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Semaphore> providerConcurrencyLimits = new ConcurrentHashMap<>();

    public ProviderResilienceManager(ModelAccessProperties properties) {
        this.properties = properties;
    }

    public Optional<ProviderCheckResponse> cachedProviderCheck(ModelProviderConfig provider) {
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

    public void cacheProviderCheck(ModelProviderConfig provider, ProviderCheckResponse response) {
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
        ProviderCircuitState state = providerCircuitStates.get(provider.id());
        return state != null && state.openUntil() != null && state.openUntil().isAfter(Instant.now());
    }

    public CircuitStateView circuitState(ModelProviderConfig provider) {
        ProviderCircuitState state = providerCircuitStates.get(provider.id());
        if (state == null) {
            return new CircuitStateView(false, 0, null);
        }
        Instant now = Instant.now();
        boolean open = state.openUntil() != null && state.openUntil().isAfter(now);
        return new CircuitStateView(open, state.consecutiveFailures(), open ? state.openUntil() : null);
    }

    public int openCircuitCount() {
        Instant now = Instant.now();
        return (int) providerCircuitStates.values()
                .stream()
                .filter(state -> state.openUntil() != null && state.openUntil().isAfter(now))
                .count();
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
        providerCircuitStates.remove(provider.id());
    }

    public void resetCircuit(ModelProviderConfig provider) {
        providerCircuitStates.remove(provider.id());
    }

    public void recordProviderFailure(ModelProviderConfig provider) {
        int threshold = properties.safeProviderCircuitFailureThreshold();
        long openMs = properties.safeProviderCircuitOpenMs();
        if (openMs <= 0) {
            return;
        }
        providerCircuitStates.compute(provider.id(), (id, existing) -> {
            boolean expiredOpenCircuit = existing != null
                    && existing.openUntil() != null
                    && existing.openUntil().isBefore(Instant.now());
            int failures = existing == null || expiredOpenCircuit ? 1 : existing.consecutiveFailures() + 1;
            Instant openUntil = failures >= threshold ? Instant.now().plusMillis(openMs) : null;
            return new ProviderCircuitState(failures, openUntil);
        });
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
        long currentWindow = Instant.now().getEpochSecond() / windowSeconds;
        RateWindow updated = providerRateWindows.compute(provider.id(), (ignored, existing) -> {
            if (existing == null || existing.window() != currentWindow) {
                return new RateWindow(currentWindow, 1);
            }
            return new RateWindow(currentWindow, existing.count() + 1);
        });
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

    private ProviderCheckResponse withCachedFlag(ProviderCheckResponse response, boolean cached) {
        return new ProviderCheckResponse(
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

    private record ProviderCircuitState(int consecutiveFailures, Instant openUntil) {
    }

    private record ProviderCheckCacheEntry(
            ProviderCheckResponse response,
            Instant providerUpdatedAt,
            Instant expiresAt
    ) {
    }

    private record RateWindow(long window, int count) {
    }

    public record CircuitStateView(boolean open, int consecutiveFailures, Instant openUntil) {
    }
}
