package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Stores WP7 raw runner artifacts under one controlled local root and only exposes opaque refs back to the API.
 * This keeps file-system topology hidden while still allowing authenticated operators to download the captured bytes.
 */
public class LocalUiE2eArtifactStorage implements UiE2eArtifactStorage {

    private static final String STORAGE_SCHEME = "artifact://ui-e2e/";

    private final Path rootDir;
    private final long maxArtifactSizeBytes;

    public LocalUiE2eArtifactStorage(UiE2eProperties properties) {
        this.rootDir = Path.of(properties.effectiveArtifactStorageDir()).toAbsolutePath().normalize();
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
        Files.createDirectories(rootDir);
        String safeType = normalizedArtifactType(artifactType);
        String extension = extension(sourceFile.getFileName() == null ? "" : sourceFile.getFileName().toString(), safeType);
        String fileName = safeType.toLowerCase(Locale.ROOT) + "-" + artifactId + extension;
        Path runDir = rootDir.resolve(runId.toString());
        Files.createDirectories(runDir);
        Path target = runDir.resolve(fileName).normalize();
        if (!target.startsWith(runDir)) {
            throw new IOException("ui-e2e artifact target escaped storage root");
        }
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredArtifact(
                STORAGE_SCHEME + runId + "/" + fileName,
                contentType(safeType, extension),
                fileName,
                sourceSize
        );
    }

    @Override
    public StoredArtifactContent read(String storageRef) throws IOException {
        Path target = resolve(storageRef);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IOException("ui-e2e artifact does not exist");
        }
        return new StoredArtifactContent(
                storageRef,
                contentTypeFromName(target.getFileName() == null ? "" : target.getFileName().toString()),
                target.getFileName() == null ? "artifact.bin" : target.getFileName().toString(),
                Files.readAllBytes(target)
        );
    }

    @Override
    public boolean isDownloadReady(String storageRef) {
        if (!StringUtils.hasText(storageRef)) {
            return false;
        }
        try {
            return Files.isRegularFile(resolve(storageRef));
        } catch (IOException ignored) {
            return false;
        }
    }

    private Path resolve(String storageRef) throws IOException {
        if (!StringUtils.hasText(storageRef) || !storageRef.startsWith(STORAGE_SCHEME)) {
            throw new IOException("ui-e2e artifact storage ref is invalid");
        }
        String relative = storageRef.substring(STORAGE_SCHEME.length());
        try {
            Path path = rootDir.resolve(relative).normalize();
            if (!path.startsWith(rootDir)) {
                throw new IOException("ui-e2e artifact storage ref escaped storage root");
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new IOException("ui-e2e artifact storage ref is invalid", exception);
        }
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
}
