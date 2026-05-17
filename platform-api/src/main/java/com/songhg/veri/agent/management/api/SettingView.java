package com.songhg.veri.agent.management.api;

public record SettingView(
        String key,
        String name,
        String value,
        String scope,
        String status
) {
}
