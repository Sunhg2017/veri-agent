package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateTestDataSetCommand(
        @NotBlank @Size(max = 64) String projectId,
        @Size(max = 64) String applicationId,
        @Size(max = 64) String environmentId,
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 32) String status,
        Map<String, Object> schema,
        @Size(max = 32) String sensitivityLevel,
        Map<String, Object> cleanupPolicy,
        @Size(max = 32) String sourceType,
        @Size(max = 64) String sourceRefDigest
) {
}
