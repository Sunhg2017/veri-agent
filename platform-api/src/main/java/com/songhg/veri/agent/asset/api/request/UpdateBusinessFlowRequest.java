package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessFlowRequest(
        @NotBlank String name,
        String description,
        Object flowJson,
        String priority,
        String status
) {
}
