package com.songhg.veri.agent.uie2e.application.query;

import java.util.UUID;

public record UiE2eBundleQuery(
        String projectId,
        UUID sceneId,
        String status,
        String keyword,
        long offset,
        int limit
) {
}
