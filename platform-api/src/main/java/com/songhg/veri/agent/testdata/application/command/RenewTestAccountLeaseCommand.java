package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotNull;

public record RenewTestAccountLeaseCommand(
        @NotNull Integer ttlSeconds
) {
}
