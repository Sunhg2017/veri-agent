package com.songhg.veri.agent.common.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * OSS-backed implementation of {@link OpaqueFileStorage} that keeps API contracts on stable opaque refs.
 */
public class OssOpaqueFileStorage implements OpaqueFileStorage {

    private final String bucket;
    private final String keyRoot;
    private final OSS ossClient;
    private final StorageRefSupport refSupport;

    public OssOpaqueFileStorage(String namespace, String bucket, String keyPrefix, OSS ossClient) {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalArgumentException("storage bucket is required");
        }
        if (ossClient == null) {
            throw new IllegalArgumentException("oss client is required");
        }
        this.bucket = bucket.trim();
        this.ossClient = ossClient;
        this.refSupport = new StorageRefSupport(namespace);
        this.keyRoot = namespaceKeyRoot(namespace, keyPrefix);
    }

    @Override
    public StoredFile store(String partition, String fileName, String contentType, Path sourceFile) throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IOException("storage source file is missing");
        }
        long sizeBytes = Files.size(sourceFile);
        String normalizedContentType = OpaqueStorageContentTypes.normalize(contentType, fileName, null);
        ObjectMetadata metadata = metadata(normalizedContentType, sizeBytes);
        String key = objectKey(partition, fileName);
        try {
            ossClient.putObject(bucket, key, sourceFile.toFile(), metadata);
            return new StoredFile(
                    refSupport.storageRef(partition, fileName),
                    normalizedContentType,
                    refSupport.safeFileName(fileName),
                    sizeBytes
            );
        } catch (OSSException | ClientException exception) {
            throw new IOException("oss storage write failed", exception);
        }
    }

    @Override
    public StoredFile storeBytes(String partition, String fileName, String contentType, byte[] content) throws IOException {
        byte[] bytes = content == null ? new byte[0] : content;
        String normalizedContentType = OpaqueStorageContentTypes.normalize(contentType, fileName, null);
        ObjectMetadata metadata = metadata(normalizedContentType, bytes.length);
        String key = objectKey(partition, fileName);
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            ossClient.putObject(bucket, key, inputStream, metadata);
            return new StoredFile(
                    refSupport.storageRef(partition, fileName),
                    normalizedContentType,
                    refSupport.safeFileName(fileName),
                    bytes.length
            );
        } catch (OSSException | ClientException exception) {
            throw new IOException("oss storage write failed", exception);
        }
    }

    @Override
    public StoredFileContent read(String storageRef) throws IOException {
        String key = objectKey(storageRef);
        try (OSSObject object = ossClient.getObject(bucket, key)) {
            if (object == null || object.getObjectContent() == null) {
                throw new IOException("stored file does not exist");
            }
            byte[] content = object.getObjectContent().readAllBytes();
            ObjectMetadata metadata = object.getObjectMetadata();
            String fileName = fileName(storageRef);
            return new StoredFileContent(
                    storageRef,
                    OpaqueStorageContentTypes.normalize(
                            metadata == null ? null : metadata.getContentType(),
                            fileName,
                            metadata == null ? null : metadata.getContentType()
                    ),
                    fileName,
                    content
            );
        } catch (OSSException exception) {
            if ("NoSuchKey".equalsIgnoreCase(exception.getErrorCode()) || "NoSuchBucket".equalsIgnoreCase(exception.getErrorCode())) {
                throw new IOException("stored file does not exist", exception);
            }
            throw new IOException("oss storage read failed", exception);
        } catch (ClientException exception) {
            throw new IOException("oss storage read failed", exception);
        }
    }

    @Override
    public boolean isDownloadReady(String storageRef) {
        if (!StringUtils.hasText(storageRef)) {
            return false;
        }
        try {
            return ossClient.doesObjectExist(bucket, objectKey(storageRef));
        } catch (IOException | RuntimeException exception) {
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
        if (batchSize <= 0) {
            return new CleanupResult(true, 0, 0, 0, 0);
        }
        Set<String> referenced = referencedStorageRefs == null ? Set.of() : Set.copyOf(referencedStorageRefs);
        Instant effectiveCutoff = cutoff == null ? Instant.EPOCH : cutoff;
        int scannedFileCount = 0;
        int deletedFileCount = 0;
        int skippedReferencedCount = 0;
        int skippedFreshCount = 0;
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Request request = new ListObjectsV2Request(bucket)
                        .withPrefix(listPrefix())
                        .withContinuationToken(continuationToken)
                        .withMaxKeys(Math.max(100, batchSize * 4));
                ListObjectsV2Result result = ossClient.listObjectsV2(request);
                for (OSSObjectSummary summary : result.getObjectSummaries()) {
                    scannedFileCount++;
                    String storageRef = storageRefFromKey(summary.getKey());
                    if (referenced.contains(storageRef)) {
                        skippedReferencedCount++;
                        continue;
                    }
                    Instant lastModified = summary.getLastModified() == null
                            ? Instant.EPOCH
                            : summary.getLastModified().toInstant();
                    if (!lastModified.isBefore(effectiveCutoff)) {
                        skippedFreshCount++;
                        continue;
                    }
                    ossClient.deleteObject(bucket, summary.getKey());
                    deletedFileCount++;
                    if (deletedFileCount >= batchSize) {
                        return new CleanupResult(true, scannedFileCount, deletedFileCount, skippedReferencedCount, skippedFreshCount);
                    }
                }
                continuationToken = result.isTruncated() ? result.getNextContinuationToken() : null;
            } while (StringUtils.hasText(continuationToken));
            return new CleanupResult(true, scannedFileCount, deletedFileCount, skippedReferencedCount, skippedFreshCount);
        } catch (OSSException | ClientException exception) {
            throw new IOException("oss cleanup failed", exception);
        }
    }

    private ObjectMetadata metadata(String contentType, long contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(Math.max(0L, contentLength));
        return metadata;
    }

    private String fileName(String storageRef) throws IOException {
        String relativePath = refSupport.relativePathFromStorageRef(storageRef);
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;
    }

    private String objectKey(String partition, String fileName) {
        return appendKeyRoot(refSupport.relativePath(partition, fileName));
    }

    private String objectKey(String storageRef) throws IOException {
        return appendKeyRoot(refSupport.relativePathFromStorageRef(storageRef));
    }

    private String storageRefFromKey(String objectKey) throws IOException {
        if (!objectKey.startsWith(listPrefix())) {
            throw new IOException("storage key is invalid");
        }
        return refSupport.storageRefFromRelative(objectKey.substring(listPrefix().length()));
    }

    private String appendKeyRoot(String relativePath) {
        return StringUtils.hasText(keyRoot) ? keyRoot + "/" + relativePath : relativePath;
    }

    private String listPrefix() {
        return StringUtils.hasText(keyRoot) ? keyRoot + "/" : "";
    }

    private static String namespaceKeyRoot(String namespace, String keyPrefix) {
        String normalizedNamespace = namespace == null ? "" : namespace.trim();
        if (!StringUtils.hasText(normalizedNamespace)) {
            throw new IllegalArgumentException("storage namespace is required");
        }
        String normalizedPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim().replace('\\', '/') : "";
        while (normalizedPrefix.startsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(1);
        }
        while (normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        return StringUtils.hasText(normalizedPrefix)
                ? normalizedPrefix + "/" + normalizedNamespace
                : normalizedNamespace;
    }
}
