package com.songhg.veri.agent.asset.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum AssetLifecycleStatus {
    ACTIVE,
    ARCHIVED,
    DELETED;

    private static final Map<AssetLifecycleStatus, Set<AssetLifecycleStatus>> TRANSITIONS = Map.of(
            ACTIVE, Set.of(ACTIVE, ARCHIVED, DELETED),
            ARCHIVED, Set.of(ACTIVE, ARCHIVED, DELETED),
            DELETED, Set.of(ACTIVE, DELETED)
    );

    private static final Set<String> CODES = Arrays.stream(values())
            .map(AssetLifecycleStatus::name)
            .collect(Collectors.toUnmodifiableSet());

    public static Set<String> codes() {
        return CODES;
    }

    public boolean canTransitionTo(AssetLifecycleStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of(this)).contains(next);
    }

    public static boolean canTransition(String currentStatus, String nextStatus) {
        try {
            return fromCode(currentStatus).canTransitionTo(fromCode(nextStatus));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String normalize(String lifecycleStatus, Instant deletedAt) {
        if (deletedAt != null) {
            return DELETED.name();
        }
        return lifecycleStatus == null || lifecycleStatus.isBlank() ? ACTIVE.name() : lifecycleStatus;
    }

    public static AssetLifecycleStatus fromCode(String value) {
        return AssetLifecycleStatus.valueOf(value);
    }
}
