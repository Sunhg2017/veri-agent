package com.songhg.veri.agent.asset.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record RollbackAssetVersionRequest(
        @Schema(description = "操作原因。")
        @Size(max = 512) String reason
) {
}
