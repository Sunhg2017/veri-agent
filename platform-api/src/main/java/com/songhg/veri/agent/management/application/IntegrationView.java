package com.songhg.veri.agent.management.application;

public record IntegrationView(
        String key,
        String name,
        String category,
        String scope,
        String status
) {
}
