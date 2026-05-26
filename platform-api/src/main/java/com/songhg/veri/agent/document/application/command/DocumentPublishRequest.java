package com.songhg.veri.agent.document.application.command;

import java.util.List;
import java.util.UUID;

public record DocumentPublishRequest(
        Boolean dryRun,
        List<UUID> candidateIds
) {
}
