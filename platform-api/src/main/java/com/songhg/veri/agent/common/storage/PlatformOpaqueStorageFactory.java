package com.songhg.veri.agent.common.storage;

import com.aliyun.oss.OSS;
import java.nio.file.Path;

/**
 * Creates namespace-scoped opaque storages so business modules stay provider-agnostic.
 */
public class PlatformOpaqueStorageFactory {

    private final PlatformStorageProperties properties;
    private final OSS ossClient;

    public PlatformOpaqueStorageFactory(PlatformStorageProperties properties, OSS ossClient) {
        this.properties = properties;
        this.ossClient = ossClient;
    }

    public OpaqueFileStorage create(String namespace) {
        return switch (properties.effectiveProvider()) {
            case "local" -> createLocal(namespace, properties.namespaceRoot(namespace));
            case "oss" -> createOss(namespace);
            default -> throw new IllegalStateException("unsupported platform storage provider");
        };
    }

    public OpaqueFileStorage createLocal(String namespace, Path rootDir) {
        return new LocalOpaqueFileStorage(namespace, rootDir);
    }

    public OpaqueFileStorage createOss(String namespace) {
        PlatformStorageProperties.OssProperties oss = properties.effectiveOss();
        if (ossClient == null) {
            throw new IllegalStateException("oss client is required when veri-agent.storage.provider=oss");
        }
        return new OssOpaqueFileStorage(
                namespace,
                oss.requiredBucket(),
                oss.normalizedKeyPrefix(),
                ossClient
        );
    }
}
