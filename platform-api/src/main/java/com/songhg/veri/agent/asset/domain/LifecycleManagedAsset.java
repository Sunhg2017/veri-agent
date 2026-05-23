package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.UUID;

public interface LifecycleManagedAsset {

    UUID id();

    String projectId();

    String lifecycleStatus();

    Instant deletedAt();

    default String currentLifecycleStatus() {
        return AssetLifecycleStatus.normalize(lifecycleStatus(), deletedAt());
    }

    default boolean canTransitionLifecycleTo(String nextLifecycleStatus) {
        return AssetLifecycleStatus.canTransition(currentLifecycleStatus(), nextLifecycleStatus);
    }
}
