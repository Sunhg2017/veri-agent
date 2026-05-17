package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.common.BusinessException;
import com.songhg.veri.agent.modelaccess.common.ErrorCode;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ProviderResilienceManager {

    private final ModelAccessProperties properties;
    private final Map<UUID, ProviderCircuitState> providerCircuitStates = new ConcurrentHashMap<>();
    private final Map<UUID, ProviderCheckCacheEntry> providerCheckCache = new ConcurrentHashMap<>();

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

    public void recordProviderSuccess(ModelProviderConfig provider) {
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
        RuntimeException lastFailure = null;
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
}
