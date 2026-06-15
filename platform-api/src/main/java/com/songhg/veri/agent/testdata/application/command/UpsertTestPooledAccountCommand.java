package com.songhg.veri.agent.testdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpsertTestPooledAccountCommand(
        @NotBlank @Size(max = 128) String accountKey,
        @Size(max = 128) String displayName,
        @Size(max = 32) String status,
        List<@Size(max = 64) String> roleTags,
        Map<String, Object> scopeSummary,
        @NotBlank @Size(max = 256) String secretRef,
        @Size(max = 32) String lastHealthStatus,
        @Size(max = 512) String lastHealthSummary
) {
}
