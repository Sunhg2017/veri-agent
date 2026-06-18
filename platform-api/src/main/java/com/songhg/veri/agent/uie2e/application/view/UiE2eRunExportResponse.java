package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;

public record UiE2eRunExportResponse(
        String schemaVersion,
        Instant exportedAt,
        UiE2eRunDetailResponse run,
        Map<String, Object> redactionPolicy
) {
}
