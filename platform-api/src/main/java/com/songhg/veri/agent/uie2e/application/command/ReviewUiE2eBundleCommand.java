package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.Size;

public record ReviewUiE2eBundleCommand(
        @Size(max = 512) String note
) {
}
