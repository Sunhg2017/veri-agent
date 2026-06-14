package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AcquireExecutionAccountLeaseCommand(
        @NotBlank @Size(max = 64) String projectId,
        @Size(max = 64) String applicationId,
        @Size(max = 64) String environmentId,
        @NotNull UUID accountPoolRef,
        List<@Size(max = 64) String> roleTags,
        @NotBlank @Size(max = 128) String executionRunRef,
        Integer ttlSeconds,
        @NotBlank @Size(max = 128) String requestKey
) {
}
