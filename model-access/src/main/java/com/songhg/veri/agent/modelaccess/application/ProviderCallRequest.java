package com.songhg.veri.agent.modelaccess.application;

public record ProviderCallRequest(
        String modelName,
        String prompt,
        String messageText
) {
}
