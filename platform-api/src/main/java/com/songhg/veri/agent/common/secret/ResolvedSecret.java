package com.songhg.veri.agent.common.secret;

public record ResolvedSecret(
        String secretRef,
        String value,
        String provider,
        String version
) {
    @Override
    public String toString() {
        return "ResolvedSecret[secretRef=%s, value=****, provider=%s, version=%s]"
                .formatted(secretRef, provider, version);
    }
}
