package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetImpactAnalysisResponse(
        String projectId,
        String subjectType,
        UUID subjectId,
        int requirementCount,
        int apiCount,
        int pageCount,
        int flowCount,
        int caseCount,
        List<AssetImpactNodeResponse> requirements,
        List<AssetImpactNodeResponse> apis,
        List<AssetImpactNodeResponse> pages,
        List<AssetImpactNodeResponse> flows,
        List<AssetImpactNodeResponse> testCases,
        List<String> gaps,
        Instant generatedAt
) {
}
