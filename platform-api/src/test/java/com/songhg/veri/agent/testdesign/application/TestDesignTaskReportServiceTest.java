package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
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
    void appendsPublishCompensationPolicyRowsWithoutPublishDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "发布补偿报告 token=secret-value",
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
        UUID retryCandidateId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID assetCaseId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        List<TestDesignPublishRecord> records = List.of(
                publishRecord(task.id(), retryCandidateId, assetCaseId, "RETRY_LINK_EXISTING", "SUCCEEDED", null),
                publishRecord(task.id(), UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        "MANUAL_LINK_EXISTING", "SUCCEEDED", null),
                publishRecord(task.id(), UUID.fromString("66666666-6666-6666-6666-666666666666"),
                        UUID.fromString("77777777-7777-7777-7777-777777777777"),
                        "DUPLICATE_REVIEW_REQUIRED", "CONFLICT", "WP3-CASE-001 high similar sourceRef=wp5:secret"),
                publishRecord(task.id(), UUID.fromString("88888888-8888-8888-8888-888888888888"),
                        null, "CREATE", "FAILED", "traceId=trace-secret token=secret-value")
        );

        TestDesignTaskReportPublishCompensationPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"), records);

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("publishCompensationPolicy,policyVersion,,wp5-publish-compensation-policy-v1")
                .contains("publishCompensationPolicy,replayKeyFamily,,AI_GENERATED_CASE_KEY")
                .contains("publishCompensationPolicy,idempotentReplaySupported,,true,,success")
                .contains("publishCompensationPolicy,partialTraceLinkRepairSupported,,true,,success")
                .contains("publishCompensationPolicy,failedCandidateRetrySupported,,true,,success")
                .contains("publishCompensationPolicy,manualConflictLinkSupported,,true,,success")
                .contains("publishCompensationPolicy,asyncCompensationBackendReady,,false,,warning")
                .contains("publishCompensationPolicy,crossWpTransactionOrchestrationReady,,false,,warning")
                .contains("publishCompensationPolicy,candidateEvidenceExported,,false")
                .contains("publishCompensationPolicy,errorTextExported,,false")
                .contains("publishCompensationPolicy,caseIdentifierListExported,,false")
                .contains("publishCompensationPolicy,traceDetailListExported,,false")
                .contains("publishCompensationPolicy,metric,retryLinkExistingCount,1,,info")
                .contains("publishCompensationPolicy,metric,linkExistingCount,0,,neutral")
                .contains("publishCompensationPolicy,metric,manualLinkExistingCount,1,,info")
                .contains("publishCompensationPolicy,metric,conflictCount,1,,warning")
                .contains("publishCompensationPolicy,metric,failedCount,1,,warning")
                .contains("publishCompensationPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain(retryCandidateId.toString())
                .doesNotContain(assetCaseId.toString())
                .doesNotContain("sourceRef")
                .doesNotContain("WP3-CASE-001")
                .doesNotContain("trace-secret")
                .doesNotContain("candidateIds")
                .doesNotContain("assetCaseIds")
                .doesNotContain("traceIds")
                .doesNotContain("reviewComments");
    }

    @Test
    void appendsContextAssemblyPolicyRowsWithoutContextDetails() {
        StringBuilder csv = new StringBuilder();
        String digest = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        UUID explicitApiId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "上下文装配报告 token=secret-value",
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
                digest,
                null,
                TestDesignContextPolicyGovernance.response(),
                Map.of(
                        "contextVersion", "wp5-context-v1",
                        "requirements", List.of(Map.of(
                                "descriptionPreview", "需求正文不应导出 token=secret-value",
                                "acceptanceCriteriaPreview", "验收标准不应导出 rawPrompt"
                        )),
                        "linkedAssetsByRequirement", List.of(Map.of(
                                "apiCount", 1,
                                "pageCount", 1,
                                "flowCount", 1,
                                "apis", List.of(Map.of("requestSchemaPreview", "{\"password\":\"secret\"}")),
                                "pages", List.of(Map.of("componentTreePreview", "page-tree-secret")),
                                "flows", List.of(Map.of("flowJsonPreview", "flow-json-secret"))
                        )),
                        "existingCasesByRequirement", List.of(Map.of(
                                "count", 1,
                                "cases", List.of(Map.of("steps", "历史用例步骤不应导出"))
                        )),
                        "explicitAssets", Map.of(
                                "apiCount", 1,
                                "pageCount", 0,
                                "flowCount", 1,
                                "apiIds", List.of(explicitApiId.toString())
                        ),
                        "limits", Map.of(
                                "linkedAssetsPerRequirement", 2,
                                "explicitAssetsPerType", 2,
                                "existingCasesPerRequirement", 2,
                                "requirementDescriptionChars", 180,
                                "acceptanceCriteriaChars", 180,
                                "linkedAssetSchemaChars", 120
                        )
                ),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportContextAssemblyPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("contextAssemblyPolicy,policyVersion,,wp5-context-assembly-policy-v1")
                .contains("contextAssemblyPolicy,assemblyMode,,SNAPSHOT_DIGEST_ONLY")
                .contains("contextAssemblyPolicy,inputDigestTracked,,true,,success")
                .contains("contextAssemblyPolicy,persistedContextSummaryOnly,,true,,success")
                .contains("contextAssemblyPolicy,wp3ApplicationServiceOnly,,true,,success")
                .contains("contextAssemblyPolicy,rawContextBodyStored,,false")
                .contains("contextAssemblyPolicy,modelPayloadStored,,false")
                .contains("contextAssemblyPolicy,digestValueExported,,false")
                .contains("contextAssemblyPolicy,requirementBodyExported,,false")
                .contains("contextAssemblyPolicy,assetSchemaExported,,false")
                .contains("contextAssemblyPolicy,pageTreeExported,,false")
                .contains("contextAssemblyPolicy,flowJsonExported,,false")
                .contains("contextAssemblyPolicy,explicitAssetIdentifierListExported,,false")
                .contains("contextAssemblyPolicy,historicalCaseStepExported,,false")
                .contains("contextAssemblyPolicy,metric,requirementSnapshotCount,1,,info")
                .contains("contextAssemblyPolicy,metric,linkedAssetSnapshotGroupCount,1,,info")
                .contains("contextAssemblyPolicy,metric,existingCaseSnapshotGroupCount,1,,info")
                .contains("contextAssemblyPolicy,metric,explicitAssetTypeCount,2,,info")
                .contains("contextAssemblyPolicy,metric,clippingLimitCount,6,,info")
                .contains("contextAssemblyPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain(digest)
                .doesNotContain(explicitApiId.toString())
                .doesNotContain("rawPrompt")
                .doesNotContain("需求正文不应导出")
                .doesNotContain("验收标准不应导出")
                .doesNotContain("page-tree-secret")
                .doesNotContain("flow-json-secret")
                .doesNotContain("历史用例步骤不应导出")
                .doesNotContain("requestSchemaPreview")
                .doesNotContain("apiIds");
    }

    @Test
    void appendsContextPolicyOperationsRowsWithoutPolicyDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "上下文策略运营报告 token=secret-value",
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

        TestDesignTaskReportContextPolicyOperationsRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("contextPolicyOperations,policyVersion,,wp5-context-policy-operations-v1")
                .contains("contextPolicyOperations,operationMode,,PLATFORM_DEFAULT_ONLY,,warning")
                .contains("contextPolicyOperations,projectOverrideStoreReady,,false,,warning")
                .contains("contextPolicyOperations,environmentOverrideStoreReady,,false,,warning")
                .contains("contextPolicyOperations,changeApprovalWorkflowReady,,false,,warning")
                .contains("contextPolicyOperations,effectivePolicySnapshotMaterialized,,true,,success")
                .contains("contextPolicyOperations,policyDiffPreviewExported,,false")
                .contains("contextPolicyOperations,approvalNotesExported,,false")
                .contains("contextPolicyOperations,ticketUrlExported,,false")
                .contains("contextPolicyOperations,projectOverrideRulesExported,,false")
                .contains("contextPolicyOperations,environmentOverrideRulesExported,,false")
                .contains("contextPolicyOperations,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("projectOverrideRuleBody")
                .doesNotContain("environmentOverrideRuleBody")
                .doesNotContain("approval-note-text")
                .doesNotContain("https://ticket.example")
                .doesNotContain("policyDocument");
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

    private static TestDesignPublishRecord publishRecord(
            UUID taskId,
            UUID candidateId,
            UUID assetCaseId,
            String action,
            String result,
            String errorMessage
    ) {
        return new TestDesignPublishRecord(
                UUID.randomUUID(),
                taskId,
                candidateId,
                "project-wp5",
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                assetCaseId,
                false,
                action,
                result,
                errorMessage,
                "auditor",
                Instant.parse("2026-05-30T00:00:00Z")
        );
    }
}
