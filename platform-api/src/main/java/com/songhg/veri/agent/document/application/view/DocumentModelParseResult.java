package com.songhg.veri.agent.document.application.view;

import com.songhg.veri.agent.document.domain.ParsedRequirementDraft;
import java.util.List;
import java.util.UUID;

public record DocumentModelParseResult(
        List<ParsedRequirementDraft> drafts,
        UUID invocationId,
        String providerName,
        String modelName,
        String errorCode,
        String errorMessage
) {

    public static DocumentModelParseResult disabled() {
        return new DocumentModelParseResult(List.of(), null, null, null, null, null);
    }

    public static DocumentModelParseResult succeeded(
            List<ParsedRequirementDraft> drafts,
            UUID invocationId,
            String providerName,
            String modelName
    ) {
        return new DocumentModelParseResult(drafts, invocationId, providerName, modelName, null, null);
    }

    public static DocumentModelParseResult failed(
            UUID invocationId,
            String providerName,
            String modelName,
            String errorCode,
            String errorMessage
    ) {
        return new DocumentModelParseResult(List.of(), invocationId, providerName, modelName, errorCode, errorMessage);
    }

    public boolean attempted() {
        return invocationId != null || errorCode != null || errorMessage != null || !drafts.isEmpty();
    }

    public boolean succeeded() {
        return errorCode == null && errorMessage == null && !drafts.isEmpty();
    }
}
