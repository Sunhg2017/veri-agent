package com.songhg.veri.agent.uie2e.application.query;

import java.util.UUID;

public record UiE2eRunQuery(
        String projectId,
        UUID sceneId,
        UUID bundleId,
        String status,
        String keyword,
        int offset,
        int limit
) {
}
