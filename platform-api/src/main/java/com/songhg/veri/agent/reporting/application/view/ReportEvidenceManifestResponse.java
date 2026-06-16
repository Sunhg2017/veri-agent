package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReportEvidenceManifestResponse(
        @Schema(description = "Evidence manifest ID")
        UUID id,
        @Schema(description = "Owning report ID")
        UUID reportId,
        @Schema(description = "Source work package")
        String sourceWp,
        @Schema(description = "Source evidence type")
        String sourceType,
        @Schema(description = "SHA-256 digest of source reference metadata")
        String sourceRefDigest,
        @Schema(description = "Evidence manifest schema version")
        String schemaVersion,
        @Schema(description = "Whitelisted summary keys included by this manifest")
        List<String> summaryKeys,
        @Schema(description = "Redaction flags for this manifest")
        Map<String, Object> redactionFlags,
        @Schema(description = "Aggregate evidence summary without raw payload")
        Map<String, Object> evidenceSummary,
        Instant createdAt
) {
}
