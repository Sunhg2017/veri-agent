package com.songhg.veri.agent.modelaccess.application.command;

public record ProviderCallRequest(
        String modelName,
        String prompt,
        String messageText
) {
}
