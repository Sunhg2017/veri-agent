package com.songhg.veri.agent.modelaccess.application.view;

public record ProviderCallResult(
        String content,
        int inputTokens,
        int outputTokens
) {
}
