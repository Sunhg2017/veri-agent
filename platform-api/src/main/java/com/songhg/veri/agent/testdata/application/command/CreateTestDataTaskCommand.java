package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record CreateTestDataTaskCommand(
        @NotBlank @Size(max = 64) String projectId,
        UUID dataSetId,
        @NotBlank @Size(max = 32) String taskType,
        @NotBlank @Size(max = 128) String requestKey,
        @Size(max = 256) String targetRef,
        Map<String, Object> resultSummary
) {
}
