package com.songhg.veri.agent.uie2e.domain;

import java.time.Instant;
import java.util.UUID;

public record UiE2eArtifactManifest(
        UUID id,
        UUID runId,
        String artifactType,
        String storageRef,
        String artifactDigest,
        long sizeBytes,
        String redactionFlagsJson,
        String captureStatus,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
