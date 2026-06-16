package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Builds the bounded WP10-to-WP2 diagnosis context without persisting or returning the context body.
 */
final class ReportDiagnosisContextBuilder {

    static final String CONTEXT_SCHEMA_VERSION = "wp10-diagnosis-context-v1";
    static final String PROMPT_MARKER = "WP10_FAILURE_DIAGNOSIS_V1";

    private static final Pattern UNSAFE_SUMMARY_KEY_PATTERN =
            Pattern.compile("(?i).*(authorization|cookie|password|passwd|secret|token|credential).*");

    private final ReportingProperties properties;
    private final ReportingJsonSupport jsonSupport;

    ReportDiagnosisContextBuilder(ReportingProperties properties, ReportingJsonSupport jsonSupport) {
        this.properties = properties;
        this.jsonSupport = jsonSupport;
    }

    DiagnosisContext build(
            ReportExecutionReport report,
            List<ReportEvidenceManifest> evidenceManifests,
            ReportFailureDiagnosis ruleDiagnosis
    ) {
        String context = contextBody(report, evidenceManifests, ruleDiagnosis);
        int maxChars = Math.max(1, properties.effectiveMaxDiagnosisContextChars() - 16);
        boolean truncated = context.length() > maxChars;
        String boundedContext = truncated ? context.substring(0, maxChars) : context;
        return new DiagnosisContext(
                boundedContext,
                Map.of(
                        "contextDigest", SensitiveTextSanitizer.sha256Hex(boundedContext),
                        "contextStored", false,
                        "bounded", true,
                        "truncated", truncated,
                        "maxChars", maxChars,
                        "sourceEvidenceManifestCount", evidenceManifests.size(),
                        "rawPromptStored", false,
                        "rawResponseStored", false
                )
        );
    }

    private String contextBody(
            ReportExecutionReport report,
            List<ReportEvidenceManifest> evidenceManifests,
            ReportFailureDiagnosis ruleDiagnosis
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schemaMarker", PROMPT_MARKER);
        context.put("schemaVersion", properties.effectiveSchemaVersion());
        context.put("contextSchemaVersion", CONTEXT_SCHEMA_VERSION);
        context.put("instruction", "Return a concise JSON failure diagnosis summary for manual review.");
        context.put("expectedOutput", Map.of(
                "schemaVersion", "wp10-diagnosis-result-v1",
                "summary", "short aggregate diagnosis",
                "rootCauseCandidates", List.of(),
                "nextActions", List.of(),
                "manualReviewRequired", true
        ));
        context.put("report", Map.of(
                "reportId", report.id(),
                "projectId", report.projectId(),
                "executionRunId", report.executionRunId(),
                "status", report.status(),
                "summary", safeContextMap(jsonSupport.readMap(report.reportSummaryJson()))
        ));
        context.put("classification", jsonSupport.readMap(ruleDiagnosis.classificationJson()));
        context.put("evidenceManifests", evidenceManifests.stream()
                .map(this::safeDiagnosisEvidence)
                .toList());
        context.put("policy", Map.of(
                "aggregateOnly", true,
                "rawRunnerArtifactIncluded", false,
                "rawPromptIncluded", false,
                "rawResponseIncluded", false,
                "secretPlaintextIncluded", false,
                "sourceRefPlaintextIncluded", false
        ));
        return jsonSupport.json(context);
    }

    private Map<String, Object> safeDiagnosisEvidence(ReportEvidenceManifest manifest) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceWp", manifest.sourceWp());
        evidence.put("sourceType", manifest.sourceType());
        evidence.put("sourceRefDigest", manifest.sourceRefDigest());
        evidence.put("schemaVersion", manifest.schemaVersion());
        evidence.put("summaryKeys", safeContextKeys(jsonSupport.readStringList(manifest.summaryKeysJson())));
        evidence.put("summary", safeContextMap(jsonSupport.readMap(manifest.evidenceSummaryJson())));
        evidence.put("redactionFlags", safeContextMap(jsonSupport.readMap(manifest.redactionFlagsJson())));
        return evidence;
    }

    private Map<String, Object> safeContextMap(Map<String, Object> source) {
        Map<String, Object> safe = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (safeContextKey(key)) {
                safe.put(key, safeContextValue(value));
            }
        });
        return safe;
    }

    private Object safeContextValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> {
                String textKey = key == null ? null : String.valueOf(key);
                if (safeContextKey(textKey)) {
                    nested.put(textKey, safeContextValue(nestedValue));
                }
            });
            return nested;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> safeValues = new ArrayList<>();
            for (Object item : values) {
                safeValues.add(safeContextValue(item));
            }
            return safeValues;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return SensitiveTextSanitizer.sanitizedEvidenceText(value == null ? null : String.valueOf(value), 128);
    }

    private List<String> safeContextKeys(List<String> keys) {
        return keys.stream()
                .filter(this::safeContextKey)
                .toList();
    }

    private boolean safeContextKey(String key) {
        return StringUtils.hasText(key) && !UNSAFE_SUMMARY_KEY_PATTERN.matcher(key).matches();
    }

    record DiagnosisContext(
            String boundedContext,
            Map<String, Object> responseMetadata
    ) {
    }
}
