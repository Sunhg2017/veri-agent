package com.songhg.veri.agent.asset.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum AssetReviewStatus {
    DRAFT,
    REVIEWING,
    APPROVED,
    DEPRECATED;

    private static final Map<AssetReviewStatus, Set<AssetReviewStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(DRAFT, REVIEWING, APPROVED, DEPRECATED),
            REVIEWING, Set.of(REVIEWING, DRAFT, APPROVED, DEPRECATED),
            APPROVED, Set.of(APPROVED, DEPRECATED),
            DEPRECATED, Set.of(DEPRECATED)
    );

    private static final Set<String> CODES = Arrays.stream(values())
            .map(AssetReviewStatus::name)
            .collect(Collectors.toUnmodifiableSet());

    public static Set<String> codes() {
        return CODES;
    }

    public boolean canTransitionTo(AssetReviewStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of(this)).contains(next);
    }

    public static boolean canTransition(String currentStatus, String nextStatus) {
        try {
            return fromCode(currentStatus).canTransitionTo(fromCode(nextStatus));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static AssetReviewStatus fromCode(String value) {
        return AssetReviewStatus.valueOf(value);
    }
}
