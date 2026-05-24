package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.constraints.Size;

public record RollbackAssetVersionRequest(
        @Size(max = 512) String reason
) {
}
