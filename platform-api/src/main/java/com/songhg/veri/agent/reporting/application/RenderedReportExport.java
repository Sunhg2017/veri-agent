package com.songhg.veri.agent.reporting.application;

/**
 * Materialized export body plus the textual representation used for digesting and redaction scanning.
 */
public record RenderedReportExport(
        Object responseContent,
        byte[] fileContent,
        String textForDigestAndScan
) {
}
