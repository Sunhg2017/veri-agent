package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUiE2eBundleCommand(
        @NotNull UUID sceneId
) {
}
