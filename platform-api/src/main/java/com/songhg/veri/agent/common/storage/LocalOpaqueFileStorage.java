package com.songhg.veri.agent.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Local-disk implementation of {@link OpaqueFileStorage}.
 *
 * <p>The implementation keeps the storage scheme stable while rejecting path traversal and limiting cleanup scans to
 * files physically located under the controlled root.</p>
 */
public class LocalOpaqueFileStorage implements OpaqueFileStorage {

    private final Path rootDir;
    private final StorageRefSupport refSupport;

    public LocalOpaqueFileStorage(String namespace, Path rootDir) {
        this.refSupport = new StorageRefSupport(namespace);
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String partition, String fileName, String contentType, Path sourceFile) throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IOException("storage source file is missing");
        }
        Path target = targetPath(partition, fileName);
        Files.createDirectories(target.getParent());
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        long sizeBytes = Files.size(target);
        return new StoredFile(
                refSupport.storageRef(partition, fileName),
                normalizedContentType(contentType, fileName, target),
                target.getFileName().toString(),
                sizeBytes
        );
    }

    @Override
    public StoredFile storeBytes(String partition, String fileName, String contentType, byte[] content) throws IOException {
        Path target = targetPath(partition, fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, content == null ? new byte[0] : content);
        long sizeBytes = Files.size(target);
        return new StoredFile(
                refSupport.storageRef(partition, fileName),
                normalizedContentType(contentType, fileName, target),
                target.getFileName().toString(),
                sizeBytes
        );
    }

    @Override
    public StoredFileContent read(String storageRef) throws IOException {
        Path target = resolve(storageRef);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IOException("stored file does not exist");
        }
        return new StoredFileContent(
                storageRef,
                normalizedContentType(null, target.getFileName().toString(), target),
                target.getFileName().toString(),
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

    @Override
    public String storageRef(String partition, String fileName) {
        return refSupport.storageRef(partition, fileName);
    }

    @Override
    public boolean supportsDestructiveCleanup() {
        return true;
    }

    @Override
    public CleanupResult cleanupUnreferenced(Set<String> referencedStorageRefs, Instant cutoff, int batchSize) throws IOException {
        if (batchSize <= 0 || !Files.exists(rootDir)) {
            return new CleanupResult(true, 0, 0, 0, 0);
        }
        Set<String> referenced = referencedStorageRefs == null ? Set.of() : Set.copyOf(referencedStorageRefs);
        Instant effectiveCutoff = cutoff == null ? Instant.EPOCH : cutoff;
        int scannedFileCount = 0;
        int deletedFileCount = 0;
        int skippedReferencedCount = 0;
        int skippedFreshCount = 0;
        try (Stream<Path> files = Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .sorted(Comparator.naturalOrder())) {
            for (Path candidate : files.toList()) {
                scannedFileCount++;
                String candidateStorageRef = storageRefFromPath(candidate);
                if (referenced.contains(candidateStorageRef)) {
                    skippedReferencedCount++;
                    continue;
                }
                FileTime lastModifiedTime = Files.getLastModifiedTime(candidate);
                if (!lastModifiedTime.toInstant().isBefore(effectiveCutoff)) {
                    skippedFreshCount++;
                    continue;
                }
                Files.deleteIfExists(candidate);
                deletedFileCount++;
                pruneEmptyParents(candidate.getParent());
                if (deletedFileCount >= batchSize) {
                    break;
                }
            }
        }
        return new CleanupResult(true, scannedFileCount, deletedFileCount, skippedReferencedCount, skippedFreshCount);
    }

    private Path resolve(String storageRef) throws IOException {
        try {
            Path target = rootDir.resolve(refSupport.relativePathFromStorageRef(storageRef)).normalize();
            if (!target.startsWith(rootDir)) {
                throw new IOException("storage ref escaped controlled root");
            }
            return target;
        } catch (InvalidPathException exception) {
            throw new IOException("storage ref is invalid", exception);
        }
    }

    private Path targetPath(String partition, String fileName) throws IOException {
        try {
            Path namespaceRoot = rootDir;
            Path target = namespaceRoot.resolve(refSupport.relativePath(partition, fileName)).normalize();
            if (!target.startsWith(namespaceRoot)) {
                throw new IOException("storage target escaped controlled root");
            }
            return target;
        } catch (InvalidPathException exception) {
            throw new IOException("storage target is invalid", exception);
        }
    }

    private String storageRefFromPath(Path candidate) {
        String relative = rootDir.relativize(candidate.toAbsolutePath().normalize()).toString().replace('\\', '/');
        return refSupport.storageRefFromRelative(relative);
    }

    private void pruneEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(rootDir) && current.startsWith(rootDir)) {
            try (Stream<Path> children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private String normalizedContentType(String contentType, String fileName, Path target) throws IOException {
        return OpaqueStorageContentTypes.normalize(contentType, fileName, target == null ? null : Files.probeContentType(target));
    }

    private String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
