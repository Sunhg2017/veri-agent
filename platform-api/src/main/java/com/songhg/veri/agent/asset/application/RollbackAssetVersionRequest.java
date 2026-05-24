package com.songhg.veri.agent.asset.application;

import jakarta.validation.constraints.Size;

public record RollbackAssetVersionRequest(
        @Size(max = 512) String reason
) {
}
