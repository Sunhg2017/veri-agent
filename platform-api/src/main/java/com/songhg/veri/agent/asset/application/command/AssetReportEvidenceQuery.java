package com.songhg.veri.agent.asset.application.command;

import java.util.List;
import java.util.UUID;

/**
 * WP10 report-evidence query for WP3 assets.
 *
 * <p>The caller supplies only stable asset references extracted from an already-sanitized upstream summary. WP3 owns
 * the project-scope validation and returns aggregate evidence without asset bodies or trace identifier lists.</p>
 */
public record AssetReportEvidenceQuery(
        String projectId,
        String reportRef,
        List<UUID> requirementRefs,
        List<UUID> apiRefs,
        List<UUID> pageRefs,
        List<UUID> businessFlowRefs,
        List<UUID> testCaseRefs
) {
}
