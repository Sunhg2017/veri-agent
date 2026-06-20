package com.songhg.veri.agent.uie2e.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public interface UiE2eArtifactStorage {

    StoredArtifact store(UUID runId, UUID artifactId, String artifactType, Path sourceFile) throws IOException;

    StoredArtifactContent read(String storageRef) throws IOException;

    boolean isDownloadReady(String storageRef);

    record StoredArtifact(
            String storageRef,
            String contentType,
            String fileName,
            long sizeBytes
    ) {
    }

    record StoredArtifactContent(
            String storageRef,
            String contentType,
            String fileName,
            byte[] content
    ) {
    }
}
