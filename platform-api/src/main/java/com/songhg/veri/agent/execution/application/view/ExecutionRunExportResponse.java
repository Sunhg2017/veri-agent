package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record ExecutionRunExportResponse(
        @Schema(description = "Export schema version")
        String schemaVersion,
        @Schema(description = "Export generation timestamp")
        Instant exportedAt,
        @Schema(description = "Sanitized execution run detail")
        ExecutionRunDetailResponse run,
        @Schema(description = "Node run status counts for the latest exported detail")
        Map<String, Integer> nodeStatusCounts,
        @Schema(description = "Export redaction and excluded-field policy")
        Map<String, Object> redactionPolicy
) {
}
