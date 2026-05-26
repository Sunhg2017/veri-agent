package com.songhg.veri.agent.modelaccess.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ProviderConcurrencyLimiter {

    Optional<Permit> tryAcquire(UUID providerId, int maxPermits);

    int availablePermits(UUID providerId, int maxPermits);

    interface Permit {

        void release();
    }
}
