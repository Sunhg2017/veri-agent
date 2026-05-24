package com.songhg.veri.agent.asset.application;

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
        List<StepDto> steps
) {
    public record StepDto(String action, String expectedResult) {
    }
}
