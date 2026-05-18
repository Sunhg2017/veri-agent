package com.songhg.veri.agent.asset.domain;

import java.util.UUID;

public record TestCaseStep(
        UUID id,
        UUID caseId,
        int stepOrder,
        String action,
        String expectedResult
) {
}
