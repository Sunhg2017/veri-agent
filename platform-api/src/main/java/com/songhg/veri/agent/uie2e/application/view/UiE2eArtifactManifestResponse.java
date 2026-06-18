package com.songhg.veri.agent.uie2e.application.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiE2eArtifactManifestResponse(
        UUID id,
        String artifactType,
        String storageRef,
        String artifactDigest,
        long sizeBytes,
        Map<String, Object> redactionFlags,
        String captureStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
