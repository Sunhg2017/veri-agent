package com.songhg.veri.agent.documentinput.api.response;

import java.time.Instant;

public record DocumentSecretProviderHealthResponse(
        String providerCode,
        String providerType,
        boolean configured,
        String status,
        int timeoutSeconds,
        int maxAttempts,
        Instant checkedAt,
        String lastErrorMessage
) {
}
