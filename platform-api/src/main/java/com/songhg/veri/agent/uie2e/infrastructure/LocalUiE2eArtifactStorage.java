package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.common.storage.LocalOpaqueFileStorage;
import com.songhg.veri.agent.common.storage.OpaqueFileStorage;
import com.songhg.veri.agent.common.storage.PlatformStorageProperties;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.nio.file.Path;

/**
 * Explicit local-disk variant used when a WP7 environment overrides the shared provider with a dedicated local path.
 */
public class LocalUiE2eArtifactStorage extends OpaqueUiE2eArtifactStorage {

    private static final String STORAGE_NAMESPACE = "ui-e2e";

    public LocalUiE2eArtifactStorage(UiE2eProperties properties) {
        this(properties, (PlatformStorageProperties) null);
    }

    public LocalUiE2eArtifactStorage(UiE2eProperties properties, PlatformStorageProperties storageProperties) {
        this(properties, localDelegate(properties, storageProperties));
    }

    public LocalUiE2eArtifactStorage(UiE2eProperties properties, OpaqueFileStorage delegate) {
        super(properties, delegate);
    }

    private static Path defaultRoot(PlatformStorageProperties storageProperties) {
        if (storageProperties != null) {
            return storageProperties.namespaceRoot(STORAGE_NAMESPACE);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "veri-agent", "storage", STORAGE_NAMESPACE)
                .toAbsolutePath()
                .normalize();
    }

    private static OpaqueFileStorage localDelegate(UiE2eProperties properties, PlatformStorageProperties storageProperties) {
        Path rootDir = properties.artifactStorageDirConfigured()
                ? Path.of(properties.effectiveArtifactStorageDir()).toAbsolutePath().normalize()
                : defaultRoot(storageProperties);
        return new LocalOpaqueFileStorage(STORAGE_NAMESPACE, rootDir);
    }
}
