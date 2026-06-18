package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.Size;

public record CancelUiE2eRunCommand(
        @Size(max = 512) String reason
) {
}
