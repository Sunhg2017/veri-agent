package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record UpdateTestCaseRequest(
        @NotBlank String title,
        String description,
        UUID requirementId,
        UUID apiId,
        String status,
        String priority,
        String tags
) {
}
