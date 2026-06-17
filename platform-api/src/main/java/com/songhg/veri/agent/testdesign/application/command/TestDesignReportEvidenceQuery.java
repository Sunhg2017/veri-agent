package com.songhg.veri.agent.testdesign.application.command;

import java.util.List;
import java.util.UUID;

/**
 * WP10 report-evidence query for WP5 generation tasks and candidates.
 */
public record TestDesignReportEvidenceQuery(
        String projectId,
        String reportRef,
        List<UUID> taskRefs,
        List<UUID> candidateRefs
) {
}
