package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.CsvEncoder;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class TestDesignTaskReportExportGovernance {

    private static final List<Pattern> FORBIDDEN_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\braw\\s*prompt\\b|\\brawPrompt\\b"),
            Pattern.compile("(?i)\\bprompt\\s*plaintext\\b|\\bpromptPlaintext\\b"),
            Pattern.compile("(?i)\\bmodel\\s*input\\b|\\bmodelInput\\b"),
            Pattern.compile("(?i)\\brequestPreview\\b|\\bresponsePreview\\b")
    );

    private TestDesignTaskReportExportGovernance() {
    }

    /**
     * Records report-level export governance using stable aggregate flags only.
     *
     * <p>These rows make the archive policy visible to operators without copying any raw candidate, review, model or
     * context bodies into the CSV.
     */
    static void appendRows(StringBuilder csv, TestDesignTaskResponse task, Instant generatedAt) {
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "reportScope", null,
                "fullTask", null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "fieldPolicy", null,
                "aggregateOnly", null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "candidateBodyAllowed", null,
                false, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "reviewCommentAllowed", null,
                false, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "modelPayloadAllowed", null,
                false, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "contextBodyAllowed", null,
                false, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "traceDetailAllowed", null,
                false, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "exportGovernance", "safetyScan", null,
                "PASSED", null, "success", "fullTask", null);
    }

    /**
     * Fails closed if a task report ever contains known raw prompt markers or unredacted secret patterns.
     *
     * <p>The report is assembled from many aggregate sections; this final scan is a last line of defense so later
     * additions cannot accidentally archive raw model/context payloads or obvious credentials.
     */
    static void validateExportSafety(String csv) {
        List<String> violations = new ArrayList<>();
        if (TestDesignSensitiveText.containsSensitiveText(csv)) {
            violations.add("sensitiveText");
        }
        for (Pattern pattern : FORBIDDEN_TEXT_PATTERNS) {
            if (pattern.matcher(csv).find()) {
                violations.add("rawPayloadMarker");
                break;
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT_BLOCKED,
                    "WP5 任务报告导出安全扫描未通过: " + String.join(",", violations));
        }
    }

    private static void appendRow(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            String recordType,
            String section,
            String metric,
            String label,
            Object value,
            String percent,
            String tone,
            String scope,
            Boolean dryRun
    ) {
        CsvEncoder.appendLine(csv,
                recordType,
                section,
                metric,
                label,
                reportValue(value),
                percent,
                tone,
                task.id(),
                exportPreview(task.title(), 200),
                task.status(),
                task.projectId(),
                scope,
                generatedAt,
                dryRun
        );
    }

    private static Object reportValue(Object value) {
        return value instanceof String text ? exportPreview(text, 240) : value;
    }

    private static String exportPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = TestDesignSensitiveText.redact(value)
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("(?i)raw\\s*prompt|rawPrompt", "[REDACTED]")
                .replaceAll("(?i)prompt\\s*plaintext|promptPlaintext", "[REDACTED]")
                .replaceAll("(?i)model\\s*input|modelInput", "[REDACTED]");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
