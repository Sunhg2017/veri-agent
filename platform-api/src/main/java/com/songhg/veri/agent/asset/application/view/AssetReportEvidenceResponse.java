package com.songhg.veri.agent.asset.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssetReportEvidenceResponse(
        String projectId,
        String reportRef,
        List<RequirementEvidence> requirements,
        List<ApiEvidence> apis,
        List<PageEvidence> pages,
        List<BusinessFlowEvidence> businessFlows,
        List<TestCaseEvidence> testCases,
        Map<String, Object> redactionPolicy
) {

    public record RequirementEvidence(
            UUID requirementRef,
            String status,
            String priority,
            int version,
            String lifecycleStatus,
            int tagCount,
            long traceLinkCount,
            long linkedApiCount,
            long linkedPageCount,
            long linkedFlowCount,
            long linkedCaseCount,
            Instant updatedAt
    ) {
    }

    public record ApiEvidence(
            UUID apiRef,
            String status,
            String lifecycleStatus,
            String httpMethod,
            int versionPresent,
            long traceLinkCount,
            long linkedRequirementCount,
            long linkedCaseCount,
            Instant updatedAt
    ) {
    }

    public record PageEvidence(
            UUID pageRef,
            String status,
            String lifecycleStatus,
            int sourceVersionPresent,
            long traceLinkCount,
            long linkedRequirementCount,
            long linkedCaseCount,
            Instant updatedAt
    ) {
    }

    public record BusinessFlowEvidence(
            UUID businessFlowRef,
            String status,
            String priority,
            String lifecycleStatus,
            long traceLinkCount,
            long linkedRequirementCount,
            long linkedCaseCount,
            Instant updatedAt
    ) {
    }

    public record TestCaseEvidence(
            UUID testCaseRef,
            String status,
            String priority,
            int version,
            String lifecycleStatus,
            int tagCount,
            int stepCount,
            UUID requirementRef,
            UUID apiRef,
            long traceLinkCount,
            Instant updatedAt
    ) {
    }
}
