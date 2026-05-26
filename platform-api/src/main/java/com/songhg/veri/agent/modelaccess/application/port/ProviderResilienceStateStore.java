package com.songhg.veri.agent.modelaccess.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProviderResilienceStateStore {

    Optional<CircuitSnapshot> circuitState(UUID providerId);

    void clearCircuit(UUID providerId);

    CircuitSnapshot recordFailure(UUID providerId, int threshold, long openMs, Instant now);

    int openCircuitCount(Instant now);

    RateLimitSnapshot incrementRateLimit(UUID providerId, long windowSeconds, Instant now);

    record CircuitSnapshot(
            /** 当前连续失败次数 */
            int consecutiveFailures,
            /** 熔断器预计关闭时间 */
            Instant openUntil
    ) {
    }

    record RateLimitSnapshot(
            /** 当前限流窗口起始时间戳 */
            long window,
            /** 当前限流窗口内累计请求数 */
            long count
    ) {
    }
}
