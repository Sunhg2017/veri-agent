package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateLinkRequest(
        @NotNull UUID requirementId,
        UUID apiId,
        UUID pageId,
        UUID flowId,
        UUID caseId
) {
}
