package com.songhg.veri.agent.asset.domain;

import java.util.List;
import java.util.Optional;

public final class AssetVersion {

    private static final int INITIAL_VERSION = 1;

    private AssetVersion() {
    }

    public static int initial() {
        return INITIAL_VERSION;
    }

    public static int next(int currentVersion) {
        return currentVersion + 1;
    }

    public static Optional<AssetVersionHistory> find(List<AssetVersionHistory> histories, int version) {
        return histories.stream()
                .filter(history -> history.version() == version)
                .findFirst();
    }
}
