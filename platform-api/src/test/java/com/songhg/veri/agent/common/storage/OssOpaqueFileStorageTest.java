package com.songhg.veri.agent.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class OssOpaqueFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesReadsAndCleansUpObjectsThroughOpaqueRefs() throws Exception {
        InMemoryOssBackend backend = new InMemoryOssBackend();
        OSS ossClient = backend.client();
        OssOpaqueFileStorage storage = new OssOpaqueFileStorage("reports", "artifact-bucket", "veri-agent/storage", ossClient);
        Path source = Files.writeString(tempDir.resolve("report.json"), "{\"status\":\"ok\"}", StandardCharsets.UTF_8);

        var stored = storage.store("report-1", "export.json", "application/json;charset=UTF-8", source);
        storage.storeBytes("report-1", "fresh.json", "application/json;charset=UTF-8", "{\"fresh\":true}".getBytes(StandardCharsets.UTF_8));
        storage.storeBytes("report-1", "stale.json", "application/json;charset=UTF-8", "{\"stale\":true}".getBytes(StandardCharsets.UTF_8));
        backend.touch("artifact-bucket", "veri-agent/storage/reports/report-1/stale.json", Instant.parse("2026-06-01T00:00:00Z"));
        backend.touch("artifact-bucket", "veri-agent/storage/reports/report-1/fresh.json", Instant.parse("2026-06-21T00:00:00Z"));

        assertThat(stored.storageRef()).isEqualTo("artifact://reports/report-1/export.json");
        assertThat(storage.isDownloadReady(stored.storageRef())).isTrue();

        var content = storage.read(stored.storageRef());
        assertThat(content.fileName()).isEqualTo("export.json");
        assertThat(content.contentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(new String(content.content(), StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"ok\"}");

        var cleanup = storage.cleanupUnreferenced(
                Set.of(stored.storageRef()),
                Instant.parse("2026-06-10T00:00:00Z"),
                10
        );

        assertThat(cleanup.supported()).isTrue();
        assertThat(cleanup.deletedFileCount()).isEqualTo(1);
        assertThat(cleanup.skippedReferencedCount()).isEqualTo(1);
        assertThat(cleanup.skippedFreshCount()).isEqualTo(1);
        assertThat(storage.isDownloadReady("artifact://reports/report-1/stale.json")).isFalse();
        assertThat(storage.isDownloadReady("artifact://reports/report-1/fresh.json")).isTrue();
    }

    private static final class InMemoryOssBackend {

        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

        OSS client() {
            return (OSS) Proxy.newProxyInstance(
                    OSS.class.getClassLoader(),
                    new Class<?>[]{OSS.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "putObject" -> handlePutObject(args);
                        case "getObject" -> handleGetObject(args);
                        case "doesObjectExist" -> handleDoesObjectExist(args);
                        case "deleteObject" -> handleDeleteObject(args);
                        case "listObjectsV2" -> handleListObjectsV2(args);
                        case "shutdown" -> null;
                        case "toString" -> "InMemoryOssBackend";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("not needed in oss storage unit test: " + method.getName());
                    }
            );
        }

        void touch(String bucketName, String key, Instant lastModified) {
            String objectId = objectId(bucketName, key);
            StoredObject current = objects.get(objectId);
            if (current != null) {
                objects.put(objectId, new StoredObject(current.content(), current.metadata(), lastModified));
            }
        }

        private Object handlePutObject(Object[] args) throws Exception {
            if (args[2] instanceof java.io.File file) {
                ObjectMetadata metadata = (ObjectMetadata) args[3];
                objects.put(objectId((String) args[0], (String) args[1]), new StoredObject(
                        Files.readAllBytes(file.toPath()),
                        metadata,
                        Instant.now()
                ));
            } else {
                java.io.InputStream inputStream = (java.io.InputStream) args[2];
                ObjectMetadata metadata = (ObjectMetadata) args[3];
                objects.put(objectId((String) args[0], (String) args[1]), new StoredObject(
                        inputStream.readAllBytes(),
                        metadata,
                        Instant.now()
                ));
            }
            return new com.aliyun.oss.model.PutObjectResult();
        }

        private Object handleGetObject(Object[] args) {
            StoredObject stored = objects.get(objectId((String) args[0], (String) args[1]));
            if (stored == null) {
                return null;
            }
            OSSObject object = new OSSObject();
            object.setBucketName((String) args[0]);
            object.setKey((String) args[1]);
            object.setObjectMetadata(stored.metadata());
            object.setObjectContent(new ByteArrayInputStream(stored.content()));
            return object;
        }

        private Object handleDoesObjectExist(Object[] args) {
            return objects.containsKey(objectId((String) args[0], (String) args[1]));
        }

        private Object handleDeleteObject(Object[] args) {
            objects.remove(objectId((String) args[0], (String) args[1]));
            return new com.aliyun.oss.model.VoidResult();
        }

        private Object handleListObjectsV2(Object[] args) {
            ListObjectsV2Request request = (ListObjectsV2Request) args[0];
            ListObjectsV2Result result = new ListObjectsV2Result();
            objects.forEach((objectId, stored) -> {
                String bucketName = request.getBucketName();
                String prefix = request.getPrefix();
                if (!objectId.startsWith(bucketName + "::")) {
                    return;
                }
                String key = objectId.substring((bucketName + "::").length());
                if (prefix != null && !key.startsWith(prefix)) {
                    return;
                }
                OSSObjectSummary summary = new OSSObjectSummary();
                summary.setBucketName(bucketName);
                summary.setKey(key);
                summary.setLastModified(Date.from(stored.lastModified()));
                summary.setSize(stored.content().length);
                result.addObjectSummary(summary);
            });
            result.setTruncated(false);
            result.setKeyCount(result.getObjectSummaries().size());
            return result;
        }

        private String objectId(String bucketName, String key) {
            return bucketName + "::" + key;
        }

        private record StoredObject(byte[] content, ObjectMetadata metadata, Instant lastModified) {
        }
    }
}
