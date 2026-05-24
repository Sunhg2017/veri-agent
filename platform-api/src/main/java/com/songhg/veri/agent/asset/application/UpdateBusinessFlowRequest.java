package com.songhg.veri.agent.asset.application;

import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessFlowRequest(
        @NotBlank String name,
        String description,
        Object flowJson,
        String priority,
        String status
) {
}
