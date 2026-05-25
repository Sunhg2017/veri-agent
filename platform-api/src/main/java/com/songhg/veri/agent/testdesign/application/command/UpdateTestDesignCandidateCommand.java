package com.songhg.veri.agent.testdesign.application.command;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record UpdateTestDesignCandidateCommand(
        @NotBlank String title,
        String description,
        UUID apiId,
        String coverageType,
        String priority,
        String preconditions,
        List<StepCommand> steps,
        String expectedResult,
        List<String> tags,
        Long version
) {
    public record StepCommand(
            String action,
            String expectedResult
    ) {
    }
}
