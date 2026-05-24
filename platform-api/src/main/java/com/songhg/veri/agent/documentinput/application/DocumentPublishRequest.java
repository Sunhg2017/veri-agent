package com.songhg.veri.agent.documentinput.application;

import java.util.List;
import java.util.UUID;

public record DocumentPublishRequest(
        Boolean dryRun,
        List<UUID> candidateIds
) {
}
