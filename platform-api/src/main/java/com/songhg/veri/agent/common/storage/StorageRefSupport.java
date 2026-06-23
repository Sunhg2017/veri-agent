package com.songhg.veri.agent.common.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Shared opaque-ref validation so local and object-storage providers enforce the same namespace and traversal rules.
 */
final class StorageRefSupport {

    private final String storageScheme;

    StorageRefSupport(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException("storage namespace is required");
        }
        this.storageScheme = "artifact://" + namespace.trim() + "/";
    }

    String storageRef(String partition, String fileName) {
        return storageScheme + relativePath(partition, fileName);
    }

    String storageRefFromRelative(String relativePath) {
        return storageScheme + normalizeRelativePath(relativePath);
    }

    String relativePath(String partition, String fileName) {
        String safeFileName = safeFileName(fileName);
        String normalizedPartition = normalizePartition(partition);
        return StringUtils.hasText(normalizedPartition)
                ? normalizedPartition + "/" + safeFileName
                : safeFileName;
    }

    String relativePathFromStorageRef(String storageRef) throws IOException {
        if (!StringUtils.hasText(storageRef) || !storageRef.startsWith(storageScheme)) {
            throw new IOException("storage ref is invalid");
        }
        try {
            return normalizeRelativePath(storageRef.substring(storageScheme.length()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("storage ref is invalid", exception);
        }
    }

    String safeFileName(String fileName) {
        String value = trimmed(fileName);
        if (!StringUtils.hasText(value) || value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("storage file name is invalid");
        }
        return value;
    }

    private String normalizePartition(String partition) {
        if (!StringUtils.hasText(partition)) {
            return "";
        }
        return normalizeRelativePath(partition);
    }

    private String normalizeRelativePath(String value) {
        String normalized = trimmed(value).replace('\\', '/');
        if (!StringUtils.hasText(normalized) || normalized.startsWith("/") || normalized.endsWith("/")) {
            throw new IllegalArgumentException("storage path is invalid");
        }
        String[] segments = normalized.split("/");
        List<String> safeSegments = new ArrayList<>(segments.length);
        for (String segment : segments) {
            String safeSegment = trimmed(segment);
            if (!StringUtils.hasText(safeSegment) || ".".equals(safeSegment) || "..".equals(safeSegment)) {
                throw new IllegalArgumentException("storage path is invalid");
            }
            safeSegments.add(safeSegment);
        }
        return String.join("/", safeSegments);
    }

    private String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
