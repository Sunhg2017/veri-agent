package com.songhg.veri.agent.uie2e.application.view;

import java.util.UUID;

public record UiE2eBatchRunItemResponse(
        UUID sceneId,
        String sceneCode,
        UUID bundleId,
        String outcome,
        String errorCode,
        String errorMessage,
        UiE2eRunDetailResponse run
) {
}
