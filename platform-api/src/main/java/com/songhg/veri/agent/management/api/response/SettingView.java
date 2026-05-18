package com.songhg.veri.agent.management.api.response;

public record SettingView(
        String key,
        String name,
        String value,
        String scope,
        String status
) {
}
