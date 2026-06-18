package com.songhg.veri.agent.uie2e.application.query;

import java.util.UUID;

public record UiE2eFlakyMarkQuery(
        String projectId,
        UUID sceneId,
        UUID runId,
        String status,
        String keyword,
        long offset,
        int limit
) {
}
