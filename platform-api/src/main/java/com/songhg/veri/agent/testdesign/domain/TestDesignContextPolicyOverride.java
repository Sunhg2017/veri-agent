package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Project/environment scoped WP5 context assembly policy override.
 *
 * <p>The record stores bounded clipping limits, approval work order metadata and the approved policy document body used
 * by policy operators. Source context bodies, raw prompts and model payloads must stay outside this aggregate; task
 * diagnostics and reports only consume aggregate readiness flags and numeric limits.
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
        String workOrderKey,
        String workOrderTitle,
        String workOrderUrl,
        String workOrderStatus,
        String policyBody,
        String policyBodyDigest,
        Integer policyBodyVersion,
        String policyDiffSummary,
        String requestNote,
        String reviewNote,
        String requestedBy,
        String approvedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
