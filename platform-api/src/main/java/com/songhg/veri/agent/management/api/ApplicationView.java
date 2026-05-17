package com.songhg.veri.agent.management.api;

public record ApplicationView(
        String name,
        String type,
        String owner,
        String version,
        String status
) {
}
