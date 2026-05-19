package com.songhg.veri.agent.documentinput.api.response;

import java.util.UUID;

public record ParsedRequirementResponse(
        String title,
        String description,
        String priority,
        String acceptanceCriteria,
        String tags,
        UUID assetRequirementId
) {
}
