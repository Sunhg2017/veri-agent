package com.songhg.veri.agent.testdesign.application.command;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record TestDesignCandidateBatchActionCommand(
        @NotBlank String action,
        List<UUID> candidateIds,
        List<Target> candidates,
        String reason,
        String comment
) {
    public record Target(
            UUID id,
            Long version
    ) {
    }
}
