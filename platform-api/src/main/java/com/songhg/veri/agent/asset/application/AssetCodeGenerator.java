package com.songhg.veri.agent.asset.application;

import java.util.UUID;

/**
 * Centralizes the persisted asset code formula so every asset entry point emits the same stable code shape.
 */
final class AssetCodeGenerator {

    private AssetCodeGenerator() {
    }

    static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
    }
}
