package com.songhg.veri.agent.management.application;

public record ApplicationView(
        String name,
        String type,
        String owner,
        String version,
        String status
) {
}
