package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;

public record UiE2eBundleExportResponse(
        String schemaVersion,
        Instant exportedAt,
        UiE2eBundleExportBundleResponse bundle,
        UiE2eBundleExportReviewSummaryResponse reviewSummary,
        Map<String, Object> redactionPolicy
) {
}
