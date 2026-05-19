package com.songhg.veri.agent.documentinput.api.request;

import java.util.List;
import java.util.UUID;

public record DocumentPublishRequest(
        Boolean dryRun,
        List<UUID> candidateIds
) {
}
