package com.songhg.veri.agent.management.application.command;

import jakarta.validation.constraints.Size;

public record UpdateIntegrationCommand(
        @Size(max = 64)
        String name,

        @Size(max = 64)
        String category,

        @Size(max = 64)
        String scope
) {
}
