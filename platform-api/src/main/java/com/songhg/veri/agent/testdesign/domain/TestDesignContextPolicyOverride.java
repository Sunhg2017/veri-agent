package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Project/environment scoped WP5 context assembly policy override.
 *
 * <p>The record stores bounded clipping limits and approval state only. It intentionally does not store policy
 * documents, diff previews, ticket URLs, approval notes or any source context body so task snapshots can reference the
 * effective policy without leaking operations details into diagnostics, model payloads or reports.
 */
public record TestDesignContextPolicyOverride(
        UUID id,
        String scopeType,
        String projectId,
        String environmentKey,
        String status,
        Integer contextLinkedAssetsPerRequirement,
        Integer contextExplicitAssetsPerType,
        Integer contextExistingCasesPerRequirement,
        Integer contextRequirementDescriptionChars,
        Integer contextAcceptanceCriteriaChars,
        Integer contextAssetSchemaChars,
        String changeReasonCode,
        String approvalReasonCode,
        String requestedBy,
        String approvedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
