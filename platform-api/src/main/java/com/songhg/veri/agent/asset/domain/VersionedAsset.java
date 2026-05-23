package com.songhg.veri.agent.asset.domain;

public interface VersionedAsset {

    int version();

    default int nextVersion() {
        return AssetVersion.next(version());
    }
}
