package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderResilienceManagerTest {

    @Test
    void retriesProviderCallsBeforePropagatingFailure() {
        ProviderResilienceManager manager = new ProviderResilienceManager(properties(2, 3, 60_000, 30_000));
        AtomicInteger attempts = new AtomicInteger();

        ProviderCallResult result = manager.callWithRetry(
                client((provider, request) -> {
                    if (attempts.incrementAndGet() < 3) {
                        throwUnavailable();
                    }
                    return new ProviderCallResult("ok", 1, 2);
                }),
                provider(Instant.now()),
                new ProviderCallRequest("model", "prompt", "message")
        );

        assertThat(result.content()).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void opensCircuitAfterFailureThresholdAndResetsOnSuccess() {
        ProviderResilienceManager manager = new ProviderResilienceManager(properties(0, 2, 60_000, 30_000));
        ModelProviderConfig provider = provider(Instant.now());

        manager.recordProviderFailure(provider);
        assertThat(manager.isCircuitOpen(provider)).isFalse();

        manager.recordProviderFailure(provider);
        assertThat(manager.isCircuitOpen(provider)).isTrue();

        manager.recordProviderSuccess(provider);
        assertThat(manager.isCircuitOpen(provider)).isFalse();
    }

    @Test
    void invalidatesProviderCheckCacheWhenProviderChanges() {
        ProviderResilienceManager manager = new ProviderResilienceManager(properties(0, 3, 60_000, 30_000));
        Instant firstUpdate = Instant.parse("2026-05-17T00:00:00Z");
        ModelProviderConfig provider = provider(firstUpdate);
        ProviderCheckResponse response = new ProviderCheckResponse(
                provider.id(),
                provider.name(),
                provider.providerType(),
                provider.status(),
                "UP",
                12,
                "model",
                null,
                null,
                false,
                Instant.now()
        );

        manager.cacheProviderCheck(provider, response);

        assertThat(manager.cachedProviderCheck(provider))
                .isPresent()
                .get()
                .extracting(ProviderCheckResponse::cached)
                .isEqualTo(true);
        assertThat(manager.cachedProviderCheck(provider(firstUpdate.plusSeconds(1)))).isEmpty();
    }

    @Test
    void propagatesLastFailureAfterAllRetriesAreExhausted() {
        ProviderResilienceManager manager = new ProviderResilienceManager(properties(1, 3, 60_000, 30_000));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> manager.callWithRetry(
                client((provider, request) -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("provider down");
                }),
                provider(Instant.now()),
                new ProviderCallRequest("model", "prompt", "message")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider down");
        assertThat(attempts).hasValue(2);
    }

    private void throwUnavailable() {
        throw new IllegalStateException("provider down");
    }

    private ModelProviderClient client(CallBehavior behavior) {
        return new ModelProviderClient() {
            @Override
            public boolean supports(ModelProviderConfig provider) {
                return true;
            }

            @Override
            public ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request) {
                return behavior.call(provider, request);
            }
        };
    }

    private ModelProviderConfig provider(Instant updatedAt) {
        return new ModelProviderConfig(
                UUID.fromString("00000000-0000-0000-0000-000000000999"),
                "test-provider",
                ProviderType.LOCAL_ECHO,
                null,
                "local://echo",
                ProviderStatus.ENABLED,
                1,
                1000,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.parse("2026-05-17T00:00:00Z"),
                updatedAt
        );
    }

    private ModelAccessProperties properties(
            int maxRetries,
            int circuitFailureThreshold,
            long circuitOpenMs,
            long checkCacheTtlMs
    ) {
        return new ModelAccessProperties(
                "test-token",
                "test-model",
                "http://localhost:8080",
                "platform-token",
                "mock",
                false,
                12_000,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                "Asia/Shanghai",
                10_000,
                maxRetries,
                circuitFailureThreshold,
                circuitOpenMs,
                checkCacheTtlMs,
                new BigDecimal("0.8")
        );
    }

    private interface CallBehavior {

        ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request);
    }
}
