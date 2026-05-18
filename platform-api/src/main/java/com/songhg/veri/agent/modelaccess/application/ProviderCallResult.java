package com.songhg.veri.agent.modelaccess.application;

public record ProviderCallResult(
        String content,
        int inputTokens,
        int outputTokens
) {
}
