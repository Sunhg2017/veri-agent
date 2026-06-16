package com.songhg.veri.agent.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WP10 reporting control-plane switches, schema versions and bounded safety limits.
 */
@ConfigurationProperties(prefix = "veri-agent.reporting")
public record ReportingProperties(
        /** Enables the reporting control plane. */
        @DefaultValue("true") boolean enabled,
        /** Allows creating report snapshots from WP9 sanitized run exports. */
        @DefaultValue("true") boolean generateEnabled,
        /** Allows AI diagnosis through WP2; rule classification remains the fallback. */
        @DefaultValue("true") boolean diagnosisEnabled,
        /** Allows platform-local defect draft creation. */
        @DefaultValue("true") boolean defectDraftEnabled,
        /** Allows sanitized JSON/Markdown summary export. */
        @DefaultValue("true") boolean exportEnabled,
        /** Maximum evidence manifest items kept in one report. */
        @DefaultValue("200") int maxEvidenceItems,
        /** Maximum sanitized context characters sent to WP2. */
        @DefaultValue("12000") int maxDiagnosisContextChars,
        /** Maximum Markdown export characters returned by the API. */
        @DefaultValue("30000") int maxExportMarkdownChars,
        /** Report snapshot schema version. */
        @DefaultValue("wp10-report-v1") String schemaVersion,
        /** Export field-set version. */
        @DefaultValue("wp10-report-export-fields-v1") String fieldSetVersion
) {
    private static final int DEFAULT_MAX_EVIDENCE_ITEMS = 200;
    private static final int MAX_EVIDENCE_ITEMS = 10_000;
    private static final int DEFAULT_MAX_DIAGNOSIS_CONTEXT_CHARS = 12_000;
    private static final int MAX_DIAGNOSIS_CONTEXT_CHARS = 100_000;
    private static final int DEFAULT_MAX_EXPORT_MARKDOWN_CHARS = 30_000;
    private static final int MAX_EXPORT_MARKDOWN_CHARS = 200_000;
    private static final String DEFAULT_SCHEMA_VERSION = "wp10-report-v1";
    private static final String DEFAULT_FIELD_SET_VERSION = "wp10-report-export-fields-v1";

    @ConstructorBinding
    public ReportingProperties {
    }

    public int effectiveMaxEvidenceItems() {
        return boundedPositive(maxEvidenceItems, DEFAULT_MAX_EVIDENCE_ITEMS, MAX_EVIDENCE_ITEMS);
    }

    public int effectiveMaxDiagnosisContextChars() {
        return boundedPositive(
                maxDiagnosisContextChars,
                DEFAULT_MAX_DIAGNOSIS_CONTEXT_CHARS,
                MAX_DIAGNOSIS_CONTEXT_CHARS
        );
    }

    public int effectiveMaxExportMarkdownChars() {
        return boundedPositive(
                maxExportMarkdownChars,
                DEFAULT_MAX_EXPORT_MARKDOWN_CHARS,
                MAX_EXPORT_MARKDOWN_CHARS
        );
    }

    public String effectiveSchemaVersion() {
        return boundedText(schemaVersion, DEFAULT_SCHEMA_VERSION, 64);
    }

    public String effectiveFieldSetVersion() {
        return boundedText(fieldSetVersion, DEFAULT_FIELD_SET_VERSION, 96);
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private static String boundedText(String value, String defaultValue, int maxLength) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
