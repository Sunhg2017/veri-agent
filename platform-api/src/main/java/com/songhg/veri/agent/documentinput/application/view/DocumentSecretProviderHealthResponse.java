package com.songhg.veri.agent.documentinput.application.view;

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
