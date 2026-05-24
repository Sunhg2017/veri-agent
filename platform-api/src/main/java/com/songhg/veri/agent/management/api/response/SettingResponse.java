package com.songhg.veri.agent.management.api.response;

public record SettingResponse(
        String key,
        String name,
        String value,
        String scope,
        String status
) {
}
