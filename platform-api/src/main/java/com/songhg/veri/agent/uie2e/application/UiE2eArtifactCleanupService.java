package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UiE2eArtifactCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UiE2eArtifactCleanupService.class);

    private final UiE2eRepository repository;
    private final UiE2eArtifactStorage artifactStorage;
    private final UiE2eProperties properties;
    private final Clock clock;

    @Autowired
    public UiE2eArtifactCleanupService(
            UiE2eRepository repository,
            UiE2eArtifactStorage artifactStorage,
            UiE2eProperties properties
    ) {
        this(repository, artifactStorage, properties, Clock.systemUTC());
    }

    UiE2eArtifactCleanupService(
            UiE2eRepository repository,
            UiE2eArtifactStorage artifactStorage,
            UiE2eProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.artifactStorage = artifactStorage;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Runs the scheduled retention cleanup only when the operator explicitly enabled destructive artifact cleanup.
     */
    public void cleanupByRetentionPolicy() {
        if (!properties.artifactCleanupEnabled()) {
            return;
        }
        cleanupNow();
    }

    /**
     * Deletes old local artifacts that are no longer referenced by any persisted run manifest.
     */
    public CleanupResult cleanupNow() {
        int retentionHours = properties.effectiveArtifactCleanupRetentionHours();
        int batchSize = properties.effectiveArtifactCleanupBatchSize();
        Instant cutoff = Instant.now(clock).minus(Duration.ofHours(retentionHours));
        if (!properties.artifactCleanupEnabled()) {
            return new CleanupResult(false, artifactStorage.supportsDestructiveCleanup(), cutoff, retentionHours, batchSize, 0, 0, 0, 0);
        }
        if (!artifactStorage.supportsDestructiveCleanup()) {
            return new CleanupResult(true, false, cutoff, retentionHours, batchSize, 0, 0, 0, 0);
        }
        try {
            Set<String> referencedStorageRefs = Set.copyOf(repository.artifactStorageRefs());
            UiE2eArtifactStorage.CleanupResult result = artifactStorage.cleanupUnreferenced(referencedStorageRefs, cutoff, batchSize);
            log.info(
                    "WP7 artifact cleanup completed cutoff={} retentionHours={} batchSize={} scanned={} deleted={} referenced={} fresh={}",
                    cutoff,
                    retentionHours,
                    batchSize,
                    result.scannedFileCount(),
                    result.deletedFileCount(),
                    result.skippedReferencedCount(),
                    result.skippedFreshCount()
            );
            return new CleanupResult(
                    true,
                    result.supported(),
                    cutoff,
                    retentionHours,
                    batchSize,
                    result.scannedFileCount(),
                    result.deletedFileCount(),
                    result.skippedReferencedCount(),
                    result.skippedFreshCount()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("WP7 artifact cleanup failed", exception);
        }
    }

    public record CleanupResult(
            boolean enabled,
            boolean supported,
            Instant cutoff,
            int retentionHours,
            int batchSize,
            int scannedFileCount,
            int deletedFileCount,
            int skippedReferencedCount,
            int skippedFreshCount
    ) {
    }
}
