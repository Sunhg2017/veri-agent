package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.common.storage.LocalOpaqueFileStorage;
import com.songhg.veri.agent.common.storage.OpaqueFileStorage;
import com.songhg.veri.agent.common.storage.PlatformStorageProperties;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Stores WP7 raw runner artifacts under one controlled local root and only exposes opaque refs back to the API.
 * This keeps file-system topology hidden while still allowing authenticated operators to download the captured bytes.
 */
public class LocalUiE2eArtifactStorage implements UiE2eArtifactStorage {

    private static final String STORAGE_NAMESPACE = "ui-e2e";

    private final OpaqueFileStorage delegate;
    private final long maxArtifactSizeBytes;

    public LocalUiE2eArtifactStorage(UiE2eProperties properties) {
        this(properties, null);
    }

    public LocalUiE2eArtifactStorage(UiE2eProperties properties, PlatformStorageProperties storageProperties) {
        Path rootDir = properties.artifactStorageDirConfigured()
                ? Path.of(properties.effectiveArtifactStorageDir()).toAbsolutePath().normalize()
                : defaultRoot(storageProperties);
        this.delegate = new LocalOpaqueFileStorage(STORAGE_NAMESPACE, rootDir);
        this.maxArtifactSizeBytes = Math.max(1L, properties.effectiveMaxArtifactSizeBytes());
    }

    @Override
    public StoredArtifact store(UUID runId, UUID artifactId, String artifactType, Path sourceFile) throws IOException {
        if (runId == null || artifactId == null || sourceFile == null || !Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IOException("ui-e2e artifact source file is missing");
        }
        long sourceSize = Files.size(sourceFile);
        if (sourceSize > maxArtifactSizeBytes) {
            throw new IOException("ui-e2e artifact exceeds configured size limit");
        }
        String safeType = normalizedArtifactType(artifactType);
        String extension = extension(sourceFile.getFileName() == null ? "" : sourceFile.getFileName().toString(), safeType);
        String fileName = safeType.toLowerCase(Locale.ROOT) + "-" + artifactId + extension;
        OpaqueFileStorage.StoredFile stored = delegate.store(runId.toString(), fileName, contentType(safeType, extension), sourceFile);
        return new StoredArtifact(stored.storageRef(), stored.contentType(), stored.fileName(), sourceSize);
    }

    @Override
    public StoredArtifactContent read(String storageRef) throws IOException {
        OpaqueFileStorage.StoredFileContent content = delegate.read(storageRef);
        return new StoredArtifactContent(
                content.storageRef(),
                content.contentType(),
                content.fileName(),
                content.content()
        );
    }

    @Override
    public boolean isDownloadReady(String storageRef) {
        return delegate.isDownloadReady(storageRef);
    }

    @Override
    public boolean supportsDestructiveCleanup() {
        return delegate.supportsDestructiveCleanup();
    }

    @Override
    public CleanupResult cleanupUnreferenced(Set<String> referencedStorageRefs, Instant cutoff, int batchSize) throws IOException {
        OpaqueFileStorage.CleanupResult result = delegate.cleanupUnreferenced(referencedStorageRefs, cutoff, batchSize);
        return new CleanupResult(
                result.supported(),
                result.scannedFileCount(),
                result.deletedFileCount(),
                result.skippedReferencedCount(),
                result.skippedFreshCount()
        );
    }

    private String normalizedArtifactType(String artifactType) {
        if (!StringUtils.hasText(artifactType)) {
            return "LOG";
        }
        return artifactType.trim().toUpperCase(Locale.ROOT);
    }

    private String extension(String fileName, String artifactType) {
        int index = fileName.lastIndexOf('.');
        if (index >= 0 && index < fileName.length() - 1) {
            return fileName.substring(index).toLowerCase(Locale.ROOT);
        }
        return switch (artifactType) {
            case "SCREENSHOT" -> ".png";
            case "TRACE" -> ".zip";
            case "VIDEO" -> ".webm";
            case "HAR" -> ".har";
            case "JUNIT_XML" -> ".xml";
            default -> ".log";
        };
    }

    private String contentType(String artifactType, String extension) {
        return switch (artifactType) {
            case "SCREENSHOT" -> MediaType.IMAGE_PNG_VALUE;
            case "TRACE" -> "application/zip";
            case "VIDEO" -> "video/webm";
            case "HAR" -> "application/json";
            case "JUNIT_XML" -> MediaType.APPLICATION_XML_VALUE;
            default -> contentTypeFromName("artifact" + extension);
        };
    }

    private String contentTypeFromName(String fileName) {
        String lowered = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lowered.endsWith(".zip")) {
            return "application/zip";
        }
        if (lowered.endsWith(".webm")) {
            return "video/webm";
        }
        if (lowered.endsWith(".xml")) {
            return MediaType.APPLICATION_XML_VALUE;
        }
        if (lowered.endsWith(".har") || lowered.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private static Path defaultRoot(PlatformStorageProperties storageProperties) {
        if (storageProperties != null) {
            return storageProperties.namespaceRoot(STORAGE_NAMESPACE);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "veri-agent", "storage", STORAGE_NAMESPACE)
                .toAbsolutePath()
                .normalize();
    }
}
