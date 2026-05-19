package com.songhg.veri.agent.documentinput.domain;

import java.util.UUID;

public record ParsedRequirementDraft(
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

    public ParsedRequirementDraft(
            String title,
            String description,
            String priority,
            String acceptanceCriteria,
            String tags,
            UUID assetRequirementId
    ) {
        this(title, description, priority, acceptanceCriteria, tags, assetRequirementId, "RULE", null, null, null);
    }

    public ParsedRequirementDraft withAssetRequirementId(UUID requirementId) {
        return new ParsedRequirementDraft(
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                requirementId,
                parseSource,
                modelInvocationId,
                modelProviderName,
                modelName
        );
    }

    public ParsedRequirementDraft withParseMetadata(
            String source,
            UUID invocationId,
            String providerName,
            String model
    ) {
        return new ParsedRequirementDraft(
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                assetRequirementId,
                source,
                invocationId,
                providerName,
                model
        );
    }
}
