package com.songhg.veri.agent.testdesign.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record CreateTestDesignTaskCommand(
        @NotBlank String projectId,
        String title,
        @NotEmpty List<UUID> requirementIds,
        List<String> coverageTypes,
        Integer caseCountPerRequirement
) {
}
