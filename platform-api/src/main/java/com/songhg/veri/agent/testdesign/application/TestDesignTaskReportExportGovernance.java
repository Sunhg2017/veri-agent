package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.CsvEncoder;
import com.songhg.veri.agent.testdesign.application.view.TestDesignArchivePolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
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
    static void appendRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignProperties properties
    ) {
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
        appendAuditPolicyRows(csv, task, generatedAt);
        appendSafetyScanPolicyRows(csv, task, generatedAt);
        appendArchivePolicyRows(csv, task, generatedAt, properties);
    }

    /**
     * Publishes audit write policy as fixed aggregate flags without copying platform audit-log records.
     *
     * <p>Task report exports already write a WP1 audit event. These rows expose that operating contract while keeping
     * audit event IDs, trace IDs and after-json details out of the CSV body.
     */
    private static void appendAuditPolicyRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt
    ) {
        appendRow(csv, task, generatedAt, "metadata", "auditPolicy", "exportAction", null,
                "EXPORT", null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "auditPolicy", "resourceType", null,
                "TEST_DESIGN_TASK_REPORT", null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "auditPolicy", "scopeType", null,
                "PROJECT", null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "auditPolicy", "auditEventWritten", null,
                true, null, "success", "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "auditPolicy", "auditDetailsExported", null,
                false, null, null, "fullTask", null);
    }

    /**
     * Exposes the final scan policy as fixed aggregate flags without exporting matched text or scan findings.
     *
     * <p>The exported rows are safe for operations reports because they only describe which policy families are
     * enforced. Violation values remain fail-closed exceptions and must not be written to the CSV body.
     */
    private static void appendSafetyScanPolicyRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt
    ) {
        appendRow(csv, task, generatedAt, "metadata", "safetyScanPolicy", "mode", null,
                "failClosed", null, "success", "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "safetyScanPolicy", "sensitiveTextPatternScan", null,
                true, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "safetyScanPolicy", "rawPayloadMarkerScan", null,
                true, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "safetyScanPolicy", "requestResponsePreviewScan", null,
                true, null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "safetyScanPolicy", "findingDetailsExported", null,
                false, null, null, "fullTask", null);
    }

    /**
     * Publishes retention and sharing policy as fixed aggregate flags for archive operators.
     *
     * <p>The policy rows are intentionally limited to enums, booleans and bounded day counts. Free-form archive notes
     * or storage locations must not be exported because they can contain tenant names, ticket IDs or secret-bearing URLs.
     */
    private static void appendArchivePolicyRows(
            StringBuilder csv,
            TestDesignTaskResponse task,
            Instant generatedAt,
            TestDesignProperties properties
    ) {
        TestDesignArchivePolicyResponse policy = TestDesignArchivePolicy.response(properties);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "policyVersion", null,
                policy.policyVersion(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "retentionDays", null,
                policy.retentionDays(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "storagePolicy", null,
                policy.storagePolicy(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "approvalRequired", null,
                policy.approvalRequired(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "archiveApprovalWorkflowReady", null,
                policy.archiveApprovalWorkflowReady(), null, "warning", "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "externalSharingAllowed", null,
                policy.externalSharingAllowed(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "retentionPolicyTracked", null,
                policy.retentionPolicyTracked(), null, "success", "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "archiveStorageReady", null,
                policy.archiveStorageReady(), null, "warning", "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "archivePathExported", null,
                policy.archivePathExported(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "archiveNotesExported", null,
                policy.archiveNotesExported(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "approvalNotesExported", null,
                policy.approvalNotesExported(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "ticketUrlExported", null,
                policy.ticketUrlExported(), null, null, "fullTask", null);
        appendRow(csv, task, generatedAt, "metadata", "archivePolicy", "aggregateOnly", null,
                policy.aggregateOnly(), null, "success", "fullTask", null);
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
