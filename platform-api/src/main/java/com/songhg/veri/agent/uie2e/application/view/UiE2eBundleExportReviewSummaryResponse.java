package com.songhg.veri.agent.uie2e.application.view;

import java.util.List;
import java.util.Map;

public record UiE2eBundleExportReviewSummaryResponse(
        int reviewCount,
        int noteCount,
        List<String> reviewStatuses,
        Map<String, Object> latestReview
) {
}
