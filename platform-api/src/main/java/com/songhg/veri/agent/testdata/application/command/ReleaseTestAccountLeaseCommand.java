package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.Size;

public record ReleaseTestAccountLeaseCommand(
        @Size(max = 256) String releaseReason,
        @Size(max = 32) String accountStatus
) {
}
