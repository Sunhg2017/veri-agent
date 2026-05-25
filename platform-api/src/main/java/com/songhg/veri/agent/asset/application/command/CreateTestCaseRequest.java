package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateTestCaseRequest(
        @NotBlank String title,
        String description,
        UUID requirementId,
        UUID apiId,
        @NotBlank
        String projectId,
        String status,
        String priority,
        String tags,
        List<StepDto> steps,
        String source,
        String sourceRef
) {
    public CreateTestCaseRequest(
            String title,
            String description,
            UUID requirementId,
            UUID apiId,
            String projectId,
            String status,
            String priority,
            String tags,
            List<StepDto> steps
    ) {
        this(title, description, requirementId, apiId, projectId, status, priority, tags, steps, null, null);
    }

    public record StepDto(String action, String expectedResult) {
    }
}
