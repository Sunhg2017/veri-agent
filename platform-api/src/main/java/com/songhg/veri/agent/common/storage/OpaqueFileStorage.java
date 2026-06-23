package com.songhg.veri.agent.common.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

/**
 * Controlled file storage that exposes only opaque references back to callers.
 *
 * <p>Modules keep user-visible downloads on this abstraction so physical paths, future provider changes, and cleanup
 * rules stay out of business services and controller contracts.</p>
 */
public interface OpaqueFileStorage {

    StoredFile store(String partition, String fileName, String contentType, Path sourceFile) throws IOException;

    StoredFile storeBytes(String partition, String fileName, String contentType, byte[] content) throws IOException;

    StoredFileContent read(String storageRef) throws IOException;

    boolean isDownloadReady(String storageRef);

    String storageRef(String partition, String fileName);

    default boolean supportsDestructiveCleanup() {
        return false;
    }

    default CleanupResult cleanupUnreferenced(Set<String> referencedStorageRefs, Instant cutoff, int batchSize) throws IOException {
        return new CleanupResult(false, 0, 0, 0, 0);
    }

    record StoredFile(
            String storageRef,
            String contentType,
            String fileName,
            long sizeBytes
    ) {
    }

    record StoredFileContent(
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
