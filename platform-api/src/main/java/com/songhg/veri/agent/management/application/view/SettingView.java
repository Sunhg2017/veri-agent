package com.songhg.veri.agent.management.application.view;

public record SettingView(
        String key,
        String name,
        String value,
        String scope,
        String status
) {
}
