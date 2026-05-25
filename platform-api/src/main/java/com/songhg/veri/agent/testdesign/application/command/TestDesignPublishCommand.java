package com.songhg.veri.agent.testdesign.application.command;

import java.util.List;
import java.util.UUID;

public record TestDesignPublishCommand(
        List<UUID> candidateIds,
        Boolean dryRun
) {
}
