package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted sanitized execution log line for one WP9 run.
 *
 * <p>Only control-plane messages and redacted metadata are stored here. Raw runner stdout/stderr, request bodies,
 * response bodies, cookies and secrets stay outside this table.</p>
 */
public record ExecutionRunLogEntry(
        UUID id,
        UUID runId,
        UUID nodeRunId,
        String level,
        String stage,
        String message,
        String metadataJson,
        Instant eventAt,
        Instant createdAt
) {
}
