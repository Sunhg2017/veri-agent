package com.songhg.veri.agent.documentinput.domain;

import java.util.UUID;

public record ParsedRequirementDraft(
        String title,
        String description,
        String priority,
        String acceptanceCriteria,
        String tags,
        UUID assetRequirementId
) {

    public ParsedRequirementDraft withAssetRequirementId(UUID requirementId) {
        return new ParsedRequirementDraft(
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                requirementId
        );
    }
}
