package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpdateTestPooledAccountCommand(
        @Size(max = 128) String displayName,
        @Size(max = 32) String status,
        List<@Size(max = 64) String> roleTags,
        Map<String, Object> scopeSummary,
        @Size(max = 256) String secretRef,
        @Size(max = 32) String lastHealthStatus,
        @Size(max = 512) String lastHealthSummary
) {
}
