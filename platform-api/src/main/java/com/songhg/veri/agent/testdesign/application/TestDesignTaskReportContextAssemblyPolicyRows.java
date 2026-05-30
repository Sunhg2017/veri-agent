package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportContextAssemblyPolicyRows {

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
        Map<String, Object> snapshot = TestDesignContextAssemblyPolicy.snapshot();
        appendMetadataRow(csv, task, generatedAt, "policyVersion", snapshot.get("policyVersion"), null);
        appendMetadataRow(csv, task, generatedAt, "assemblyMode", snapshot.get("assemblyMode"), null);
        appendMetadataRow(csv, task, generatedAt, "digestStrategy", snapshot.get("digestStrategy"), null);
        appendMetadataRow(csv, task, generatedAt, "inputDigestRequired",
                snapshot.get("inputDigestRequired"), "success");
        appendMetadataRow(csv, task, generatedAt, "inputDigestTracked",
                StringUtils.hasText(task.inputDigest()), StringUtils.hasText(task.inputDigest()) ? "success" : "warning");
        appendMetadataRow(csv, task, generatedAt, "persistedContextSummaryOnly",
                snapshot.get("persistedContextSummaryOnly"), "success");
        appendMetadataRow(csv, task, generatedAt, "wp3ApplicationServiceOnly",
                snapshot.get("wp3ApplicationServiceOnly"), "success");
        appendMetadataRow(csv, task, generatedAt, "rawContextBodyStored", snapshot.get("rawContextBodyStored"), null);
        appendMetadataRow(csv, task, generatedAt, "modelPayloadStored", snapshot.get("modelPayloadStored"), null);
        appendMetadataRow(csv, task, generatedAt, "digestValueExported", snapshot.get("digestValueExported"), null);
        appendMetadataRow(csv, task, generatedAt, "requirementBodyExported",
                snapshot.get("requirementBodyExported"), null);
        appendMetadataRow(csv, task, generatedAt, "assetSchemaExported", snapshot.get("assetSchemaExported"), null);
        appendMetadataRow(csv, task, generatedAt, "pageTreeExported", snapshot.get("pageTreeExported"), null);
        appendMetadataRow(csv, task, generatedAt, "flowJsonExported", snapshot.get("flowJsonExported"), null);
        appendMetadataRow(csv, task, generatedAt, "explicitAssetIdentifierListExported",
                snapshot.get("explicitAssetIdentifierListExported"), null);
        appendMetadataRow(csv, task, generatedAt, "historicalCaseStepExported",
                snapshot.get("historicalCaseStepExported"), null);
        appendMetricRow(csv, task, generatedAt, "requirementSnapshotCount", listSize(context.get("requirements")));
        appendMetricRow(csv, task, generatedAt, "linkedAssetSnapshotGroupCount",
                listSize(context.get("linkedAssetsByRequirement")));
        appendMetricRow(csv, task, generatedAt, "existingCaseSnapshotGroupCount",
                listSize(context.get("existingCasesByRequirement")));
        appendMetricRow(csv, task, generatedAt, "explicitAssetTypeCount",
                explicitAssetTypeCount(context.get("explicitAssets")));
        appendMetricRow(csv, task, generatedAt, "clippingLimitCount", clippingLimitCount(context.get("limits")));
        appendMetadataRow(csv, task, generatedAt, "aggregateOnly", snapshot.get("aggregateOnly"), "success");
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
