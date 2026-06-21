package com.songhg.veri.agent.uie2e.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface UiE2eArtifactStorage {

    StoredArtifact store(UUID runId, UUID artifactId, String artifactType, Path sourceFile) throws IOException;

    StoredArtifactContent read(String storageRef) throws IOException;

    boolean isDownloadReady(String storageRef);

    default boolean supportsDestructiveCleanup() {
        return false;
    }

    default CleanupResult cleanupUnreferenced(Set<String> referencedStorageRefs, Instant cutoff, int batchSize) throws IOException {
        return new CleanupResult(false, 0, 0, 0, 0);
    }

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

    record CleanupResult(
            boolean supported,
            int scannedFileCount,
            int deletedFileCount,
            int skippedReferencedCount,
            int skippedFreshCount
    ) {
    }
}
