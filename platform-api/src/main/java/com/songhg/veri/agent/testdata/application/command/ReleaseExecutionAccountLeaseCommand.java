package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReleaseExecutionAccountLeaseCommand(
        @NotBlank @Size(max = 128) String executionRunRef,
        @Size(max = 256) String releaseReason,
        @Size(max = 32) String accountStatus
) {
}
