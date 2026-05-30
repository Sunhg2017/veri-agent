package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDesignTaskReportServiceTest {

    @Test
    void allowsAggregateOnlyTaskReportRows() {
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety("""
                recordType,section,metric,label,value
                metadata,exportGovernance,fieldPolicy,,aggregateOnly
                metadata,auditPolicy,auditEventWritten,,true
                metadata,safetyScanPolicy,mode,,failClosed
                metadata,archivePolicy,retentionDays,,180
                metadata,task,promptKey,,wp5-test-design-v1
                summary,candidateQuality,metric,publishable,2
                """));
    }

    @Test
    void appendsReportManifestRowsWithoutDetailFields() {
        StringBuilder csv = new StringBuilder("""
                recordType,section,metric,label,value,percent,tone,taskId,taskTitle,taskStatus,projectId,scope,generatedAt,dryRun
                metadata,task,reportType,,WP5_TASK_REPORT_FULL,,,11111111-1111-1111-1111-111111111111,报告任务,SUCCEEDED,project-wp5,fullTask,2026-05-30T00:00:00Z,
                summary,candidateQuality,metric,publishable,1,100.00,,11111111-1111-1111-1111-111111111111,报告任务,SUCCEEDED,project-wp5,fullTask,2026-05-30T00:00:00Z,
                """);
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "报告 manifest token=secret-value",
                "SUCCEEDED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                null,
                TestDesignContextPolicyGovernance.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportService.appendTaskReportManifestRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("reportManifest,schemaVersion,,wp5-task-report-v1")
                .contains("reportManifest,fieldSetVersion,,aggregate-only-v1")
                .contains("reportManifest,rowCountBeforeManifest,,2")
                .contains("reportManifest,aggregateOnly,,true,,success")
                .contains("reportManifest,detailRowsExported,,false")
                .contains("reportManifest,manifestStatus,,COMPLETE,,success")
                .doesNotContain("secret-value")
                .doesNotContain("candidateIds")
                .doesNotContain("auditLogIds")
                .doesNotContain("traceIds")
                .doesNotContain("rowDigest");
    }

    @Test
    void appendsBoundedArchivePolicyRowsWithoutFreeText() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "归档报告 token=secret-value",
                "SUCCEEDED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                null,
                TestDesignContextPolicyGovernance.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportExportGovernance.appendRows(csv, task, Instant.parse("2026-05-30T00:00:00Z"),
                properties(9999, true, false));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("auditPolicy,exportAction,,EXPORT")
                .contains("auditPolicy,resourceType,,TEST_DESIGN_TASK_REPORT")
                .contains("auditPolicy,scopeType,,PROJECT")
                .contains("auditPolicy,auditEventWritten,,true,,success")
                .contains("auditPolicy,auditDetailsExported,,false")
                .contains("safetyScanPolicy,mode,,failClosed,,success")
                .contains("safetyScanPolicy,sensitiveTextPatternScan,,true")
                .contains("safetyScanPolicy,rawPayloadMarkerScan,,true")
                .contains("safetyScanPolicy,requestResponsePreviewScan,,true")
                .contains("safetyScanPolicy,findingDetailsExported,,false")
                .contains("archivePolicy,retentionDays,,3650")
                .contains("archivePolicy,storagePolicy,,platformManaged")
                .contains("archivePolicy,approvalRequired,,false")
                .contains("archivePolicy,externalSharingAllowed,,true")
                .contains("archivePolicy,retentionPolicyTracked,,true,,success")
                .doesNotContain("auditLogId")
                .doesNotContain("afterJson")
                .doesNotContain("secret-value");
    }

    @Test
    void appendsPromptCalibrationPolicyRowsWithoutSampleDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "Prompt 校准报告 token=secret-value",
                "SUCCEEDED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                null,
                TestDesignContextPolicyGovernance.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportPromptCalibrationPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"), 3L, 2L, 1L);

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("promptCalibrationPolicy,policyVersion,,wp5-prompt-calibration-policy-v1")
                .contains("promptCalibrationPolicy,sampleSource,,HUMAN_FEEDBACK_AGGREGATE")
                .contains("promptCalibrationPolicy,calibrationStatus,,AGGREGATE_SIGNALS_ONLY")
                .contains("promptCalibrationPolicy,metric,feedbackSignalsTracked,3,,info")
                .contains("promptCalibrationPolicy,metric,sampleCandidatesTracked,2,,info")
                .contains("promptCalibrationPolicy,metric,sampleExplanationCount,1,,info")
                .contains("promptCalibrationPolicy,sampleSetMaintenanceWorkflowReady,,false,,warning")
                .contains("promptCalibrationPolicy,longTermCalibrationBaselineReady,,false,,warning")
                .contains("promptCalibrationPolicy,sampleDetailRowsExported,,false")
                .contains("promptCalibrationPolicy,candidateBodyExported,,false")
                .contains("promptCalibrationPolicy,reviewTextExported,,false")
                .contains("promptCalibrationPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("sampleCandidateIds")
                .doesNotContain("candidateIds")
                .doesNotContain("reviewComments")
                .doesNotContain("promptPlaintext");
    }

    @Test
    void appendsReadinessPolicyRowsWithoutCandidateEvidence() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "质量准出报告 token=secret-value",
                "SUCCEEDED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                null,
                TestDesignContextPolicyGovernance.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );
        TestDesignQualityReadinessResponse readiness = new TestDesignQualityReadinessResponse(
                "BLOCKED",
                1L,
                1L,
                List.of(
                        new TestDesignQualityReadinessCheckResponse(
                                "stepComplete", "步骤完整率", "FAILED", "BLOCKING", 50D, 100D, "PERCENT",
                                "步骤动作和步骤预期均完整的候选占比不得低于阈值"),
                        new TestDesignQualityReadinessCheckResponse(
                                "lowConfidence", "低置信度占比", "FAILED", "WARNING", 25D, 20D, "PERCENT",
                                "低置信度候选占比不得高于阈值")
                )
        );

        TestDesignTaskReportReadinessPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"), readiness);

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("readinessPolicy,policyVersion,,wp5-quality-readiness-policy-v1")
                .contains("readinessPolicy,thresholdSource,,DEPLOY_CONFIG")
                .contains("readinessPolicy,advisoryOnly,,true,,warning")
                .contains("readinessPolicy,publishBlockingEnabled,,false,,warning")
                .contains("readinessPolicy,readinessStatus,,BLOCKED,,warning")
                .contains("readinessPolicy,metric,blockingCount,1,,warning")
                .contains("readinessPolicy,metric,warningCount,1,,warning")
                .contains("readinessPolicy,checkStatus,stepComplete,FAILED,,warning")
                .contains("readinessPolicy,currentValue,stepComplete,50.0")
                .contains("readinessPolicy,thresholdValue,stepComplete,100.0")
                .contains("readinessPolicy,unit,stepComplete,PERCENT")
                .contains("readinessPolicy,severity,stepComplete,BLOCKING")
                .contains("readinessPolicy,checkStatus,lowConfidence,FAILED,,warning")
                .contains("readinessPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("candidateIds")
                .doesNotContain("candidateBody")
                .doesNotContain("reviewComments")
                .doesNotContain("promptPlaintext")
                .doesNotContain("步骤动作和步骤预期");
    }

    @Test
    void blocksRawPromptMarkersAndUnredactedSecrets() {
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,task,rawPrompt,,secret"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,publish,error,,Bearer abcdefgh123"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,model,error,,sk_live_12345678"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,model,error,,token=secret-value"));
    }

    private static TestDesignProperties properties(
            int reportArchiveRetentionDays,
            boolean externalSharingAllowed,
            boolean approvalRequired
    ) {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "RULE_TEMPLATE",
                "wp5-test-design-v1",
                "1.0.0",
                false,
                20,
                3,
                5,
                5,
                5,
                240,
                240,
                240,
                100,
                true,
                true,
                100,
                600,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                0.86D,
                0.90D,
                reportArchiveRetentionDays,
                externalSharingAllowed,
                approvalRequired
        );
    }
}
