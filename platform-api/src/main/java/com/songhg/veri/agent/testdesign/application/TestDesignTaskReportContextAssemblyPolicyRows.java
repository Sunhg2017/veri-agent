package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportContextAssemblyPolicyRows {

    private static final String POLICY_VERSION = "wp5-context-assembly-policy-v1";

    private TestDesignTaskReportContextAssemblyPolicyRows() {
    }

    /**
     * Appends context assembly provenance and safety boundaries without copying context detail payloads.
     *
     * <p>The report needs to prove that WP5 assembled a digest-backed snapshot from WP3/WP4-facing summaries, but it
     * must not archive requirement bodies, asset schemas, page trees, flow JSON, explicit asset identifier lists or
     * historical case steps. These rows expose only fixed flags and bounded aggregate counters.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        Map<String, Object> context = task.contextSummary() == null ? Map.of() : task.contextSummary();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", POLICY_VERSION, null);
        appendMetadataRow(csv, task, generatedAt, "assemblyMode", "SNAPSHOT_DIGEST_ONLY", null);
        appendMetadataRow(csv, task, generatedAt, "inputDigestTracked",
                StringUtils.hasText(task.inputDigest()), StringUtils.hasText(task.inputDigest()) ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "persistedContextSummaryOnly", true, "success");
        appendMetadataRow(csv, task, generatedAt, "wp3ApplicationServiceOnly", true, "success");
        appendMetadataRow(csv, task, generatedAt, "rawContextBodyStored", false, null);
        appendMetadataRow(csv, task, generatedAt, "modelPayloadStored", false, null);
        appendMetadataRow(csv, task, generatedAt, "digestValueExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "requirementBodyExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "assetSchemaExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "pageTreeExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "flowJsonExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "explicitAssetIdentifierListExported", false, null);
        appendMetadataRow(csv, task, generatedAt, "historicalCaseStepExported", false, null);
        appendMetricRow(csv, task, generatedAt, "requirementSnapshotCount", listSize(context.get("requirements")));
        appendMetricRow(csv, task, generatedAt, "linkedAssetSnapshotGroupCount",
                listSize(context.get("linkedAssetsByRequirement")));
        appendMetricRow(csv, task, generatedAt, "existingCaseSnapshotGroupCount",
                listSize(context.get("existingCasesByRequirement")));
        appendMetricRow(csv, task, generatedAt, "explicitAssetTypeCount",
                explicitAssetTypeCount(context.get("explicitAssets")));
        appendMetricRow(csv, task, generatedAt, "clippingLimitCount", clippingLimitCount(context.get("limits")));
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", true, "success");
    }

    private static long listSize(Object value) {
        return value instanceof List<?> items ? items.size() : 0L;
    }

    private static long explicitAssetTypeCount(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return 0L;
        }
        return List.of("apiCount", "pageCount", "flowCount").stream()
                .filter(field -> positiveNumber(map.get(field)))
                .count();
    }

    private static long clippingLimitCount(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return 0L;
        }
        return map.values().stream().filter(TestDesignTaskReportContextAssemblyPolicyRows::positiveNumber).count();
    }

    private static boolean positiveNumber(Object value) {
        if (value instanceof Number number) {
            return number.longValue() > 0L;
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text) > 0L;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return false;
    }

    private static void appendMetadataRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String metric,
            Object value,
            String tone
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "metadata", "contextAssemblyPolicy", metric, null, value, null, tone, "fullTask", null);
    }

    private static void appendMetricRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String label,
            long value
    ) {
        TestDesignTaskReportService.appendTaskReportRow(csv, task, generatedAt,
                "summary", "contextAssemblyPolicy", "metric", label, value, null,
                value > 0L ? "info" : "neutral", "fullTask", null);
    }
}
