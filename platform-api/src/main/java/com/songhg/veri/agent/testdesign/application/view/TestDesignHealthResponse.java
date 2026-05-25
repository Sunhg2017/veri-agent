package com.songhg.veri.agent.testdesign.application.view;

import java.util.List;

public record TestDesignHealthResponse(
        String service,
        String status,
        boolean generationEnabled,
        String generationMode,
        String promptKey,
        String promptVersion,
        int maxRequirementsPerTask,
        int maxCasesPerRequirement,
        List<String> supportedCoverageTypes
) {
}
