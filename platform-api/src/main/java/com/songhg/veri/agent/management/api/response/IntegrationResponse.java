package com.songhg.veri.agent.management.api.response;

public record IntegrationResponse(
        String key,
        String name,
        String category,
        String scope,
        String status
) {
}
