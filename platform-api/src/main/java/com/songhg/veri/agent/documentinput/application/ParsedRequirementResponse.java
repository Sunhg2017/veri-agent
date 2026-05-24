package com.songhg.veri.agent.documentinput.application;

import java.util.UUID;

public record ParsedRequirementResponse(
        String title,
        String description,
        String priority,
        String acceptanceCriteria,
        String tags,
        UUID assetRequirementId,
        String parseSource,
        UUID modelInvocationId,
        String modelProviderName,
        String modelName
) {
}
