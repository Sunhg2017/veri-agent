package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.testdesign.application.view.TestDesignArchivePolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportManifestPolicyResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import java.math.BigDecimal;
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
                metadata,reportManifestPolicy,manifestMode,,AGGREGATE_RECONCILIATION
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportManifestRows.appendRows(
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
    void returnsAggregateManifestSnapshotForPersistence() {
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportManifestRows.ManifestSnapshot snapshot = TestDesignTaskReportManifestRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        org.assertj.core.api.Assertions.assertThat(snapshot.schemaVersion()).isEqualTo("wp5-task-report-v1");
        org.assertj.core.api.Assertions.assertThat(snapshot.fieldSetVersion()).isEqualTo("aggregate-only-v1");
        org.assertj.core.api.Assertions.assertThat(snapshot.manifestMode()).isEqualTo("AGGREGATE_RECONCILIATION");
        org.assertj.core.api.Assertions.assertThat(snapshot.rowCountBeforeManifest()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(snapshot.reportRowCount()).isEqualTo(8L);
        org.assertj.core.api.Assertions.assertThat(snapshot.aggregateOnly()).isTrue();
        org.assertj.core.api.Assertions.assertThat(snapshot.detailRowsExported()).isFalse();
        org.assertj.core.api.Assertions.assertThat(snapshot.manifestStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void appendsReportManifestPolicyRowsWithoutRowOrIdentifierDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "报告清单策略 token=secret-value",
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                reportManifestPolicy(),
                Map.of("reportManifestPolicy", Map.of(
                        "rowHashes", List.of("row-hash-secret"),
                        "candidateIds", List.of("candidate-secret-id"),
                        "traceIds", List.of("trc_secret"),
                        "auditLogIds", List.of("audit-secret-id"),
                        "rowSummaries", List.of("row summary should not appear")
                )),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportManifestPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("reportManifestPolicy,policyVersion,,wp5-report-manifest-policy-v1")
                .contains("reportManifestPolicy,schemaVersion,,wp5-task-report-v1")
                .contains("reportManifestPolicy,fieldSetVersion,,aggregate-only-v1")
                .contains("reportManifestPolicy,manifestMode,,AGGREGATE_RECONCILIATION")
                .contains("reportManifestPolicy,rowCountTracked,,true,,success")
                .contains("reportManifestPolicy,completionStatusTracked,,true,,success")
                .contains("reportManifestPolicy,archiveReconciliationReady,,true,,success")
                .contains("reportManifestPolicy,detailRowsExported,,false")
                .contains("reportManifestPolicy,rowIntegrityValueExported,,false")
                .contains("reportManifestPolicy,rowContentSummaryExported,,false")
                .contains("reportManifestPolicy,candidateIdentifierListExported,,false")
                .contains("reportManifestPolicy,traceIdentifierListExported,,false")
                .contains("reportManifestPolicy,auditIdentifierListExported,,false")
                .contains("reportManifestPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("row-hash-secret")
                .doesNotContain("candidate-secret-id")
                .doesNotContain("trc_secret")
                .doesNotContain("audit-secret-id")
                .doesNotContain("row summary should not appear")
                .doesNotContain("candidateIds")
                .doesNotContain("traceIds")
                .doesNotContain("auditLogIds");
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
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
                .contains("archivePolicy,policyVersion,,wp5-archive-policy-v1")
                .contains("archivePolicy,retentionDays,,3650")
                .contains("archivePolicy,storagePolicy,,platformManaged")
                .contains("archivePolicy,approvalRequired,,false")
                .contains("archivePolicy,archiveApprovalWorkflowReady,,false,,warning")
                .contains("archivePolicy,externalSharingAllowed,,true")
                .contains("archivePolicy,retentionPolicyTracked,,true,,success")
                .contains("archivePolicy,archiveStorageReady,,false,,warning")
                .contains("archivePolicy,archivePathExported,,false")
                .contains("archivePolicy,archiveNotesExported,,false")
                .contains("archivePolicy,approvalNotesExported,,false")
                .contains("archivePolicy,ticketUrlExported,,false")
                .contains("archivePolicy,aggregateOnly,,true,,success")
                .doesNotContain("auditLogId")
                .doesNotContain("afterJson")
                .doesNotContain("s3://")
                .doesNotContain("archive-note-text")
                .doesNotContain("approval-note-text")
                .doesNotContain("https://ticket.example")
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
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
                .contains("publishCompensationPolicy,asyncCompensationBackendReady,,true,,success")
                .contains("publishCompensationPolicy,compensationCandidateScope,,FAILED_WITH_EXISTING_WP3_CASE_REFERENCE")
                .contains("publishCompensationPolicy,autoConflictResolutionEnabled,,false,,success")
                .contains("publishCompensationPolicy,autoFirstTimeCreateEnabled,,false,,success")
                .contains("publishCompensationPolicy,crossWpTransactionOrchestrationReady,,false,,warning")
                .contains("publishCompensationPolicy,candidateEvidenceExported,,false")
                .contains("publishCompensationPolicy,errorTextExported,,false")
                .contains("publishCompensationPolicy,caseIdentifierListExported,,false")
                .contains("publishCompensationPolicy,traceDetailListExported,,false")
                .contains("publishCompensationPolicy,metric,autoCompensateLinkExistingCount,0,,neutral")
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
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
                .contains("contextAssemblyPolicy,policyVersion,,wp5-context-assembly-policy-v2")
                .contains("contextAssemblyPolicy,assemblyMode,,SNAPSHOT_DIGEST_ONLY")
                .contains("contextAssemblyPolicy,digestStrategy,,SHA256_CONTEXT_SUMMARY")
                .contains("contextAssemblyPolicy,inputDigestRequired,,true,,success")
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportContextPolicyOperationsRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("contextPolicyOperations,policyVersion,,wp5-context-policy-operations-v2")
                .contains("contextPolicyOperations,operationMode,,PLATFORM_DEFAULT_ONLY,,warning")
                .contains("contextPolicyOperations,policyResolutionOrder,,PLATFORM_DEFAULT_ONLY,,warning")
                .contains("contextPolicyOperations,policyFallbackBehavior,,DEPLOY_CONFIG_CHANGE_REQUIRED,,warning")
                .contains("contextPolicyOperations,approvalStatus,,WORKFLOW_NOT_READY,,warning")
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
    void appendsScopePolicyRowsWithoutRoleOrTokenDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "作用域策略报告 token=secret-value",
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of("scopePolicy", Map.of(
                        "candidateIds", List.of("candidate-secret-id"),
                        "roleRuleDetails", "role matrix should not appear",
                        "serviceTokenValue", "token=secret-value"
                )),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportScopePolicyRows.appendRows(csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("scopePolicy,policyVersion,,wp5-scope-policy-v1")
                .contains("scopePolicy,scopeModel,,PROJECT_RESOURCE_SCOPE,,success")
                .contains("scopePolicy,listFallbackScope,,PLATFORM_WHEN_PROJECT_FILTER_ABSENT,,warning")
                .contains("scopePolicy,taskProjectScopeRequired,,true,,success")
                .contains("scopePolicy,candidateProjectScopeRequired,,true,,success")
                .contains("scopePolicy,batchCandidateProjectScopeRequired,,true,,success")
                .contains("scopePolicy,publishProjectScopeRequired,,true,,success")
                .contains("scopePolicy,asyncTaskProjectScopeRecovered,,true,,success")
                .contains("scopePolicy,smokeProjectScopeRequired,,true,,success")
                .contains("scopePolicy,evaluationCorpusProjectIsolated,,true,,success")
                .contains("scopePolicy,evaluationCorpusOperationsReady,,false,,warning")
                .contains("scopePolicy,crossWpScopeDashboardReady,,false,,warning")
                .contains("scopePolicy,candidateIdentifierListExported,,false")
                .contains("scopePolicy,roleRuleDetailExported,,false")
                .contains("scopePolicy,serviceTokenValueExported,,false")
                .contains("scopePolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("candidate-secret-id")
                .doesNotContain("role matrix should not appear")
                .doesNotContain("candidateIds")
                .doesNotContain("roleRuleDetails");
    }

    @Test
    void appendsEvaluationCorpusPolicyRowsWithoutSampleOrPromptDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "评测语料策略报告 token=secret-value",
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of("evaluationCorpusPolicy", Map.of(
                        "corpusRows", List.of("sample-row-secret"),
                        "candidateBody", "候选正文不应导出",
                        "reviewComment", "review comment should not appear",
                        "promptPlaintext", "prompt body should not appear"
                )),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportEvaluationCorpusPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("evaluationCorpusPolicy,policyVersion,,wp5-evaluation-corpus-policy-v1")
                .contains("evaluationCorpusPolicy,corpusMode,,GOLDEN_SET_BASELINE,,success")
                .contains("evaluationCorpusPolicy,qualityGateMode,,MANUAL_OPT_IN_AI_EVAL,,warning")
                .contains("evaluationCorpusPolicy,thresholdSource,,DEPLOY_CONFIG")
                .contains("evaluationCorpusPolicy,projectScopeRequired,,true,,success")
                .contains("evaluationCorpusPolicy,goldenSetBaselineRequired,,true,,success")
                .contains("evaluationCorpusPolicy,qualityEvalScriptReady,,true,,success")
                .contains("evaluationCorpusPolicy,qualityGateIntegrated,,true,,success")
                .contains("evaluationCorpusPolicy,readinessDistributionTracked,,true,,success")
                .contains("evaluationCorpusPolicy,promptVersionTracked,,true,,success")
                .contains("evaluationCorpusPolicy,evaluationCorpusProjectIsolated,,true,,success")
                .contains("evaluationCorpusPolicy,sampleMaintenanceReady,,false,,warning")
                .contains("evaluationCorpusPolicy,longTermCalibrationReady,,false,,warning")
                .contains("evaluationCorpusPolicy,operationsConsoleReady,,false,,warning")
                .contains("evaluationCorpusPolicy,corpusRowExported,,false")
                .contains("evaluationCorpusPolicy,candidateBodyExported,,false")
                .contains("evaluationCorpusPolicy,reviewCommentExported,,false")
                .contains("evaluationCorpusPolicy,promptBodyExported,,false")
                .contains("evaluationCorpusPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("sample-row-secret")
                .doesNotContain("候选正文不应导出")
                .doesNotContain("review comment should not appear")
                .doesNotContain("prompt body should not appear")
                .doesNotContain("corpusRows")
                .doesNotContain("promptPlaintext");
    }

    @Test
    void appendsModelObservationPolicyRowsWithoutObservationDetails() {
        StringBuilder csv = new StringBuilder();
        UUID invocationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID jobId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "模型观测策略报告 token=secret-value",
                "FAILED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                invocationId,
                "local-echo-primary",
                "local-echo",
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                new TestDesignModelObservationResponse(
                        invocationId,
                        jobId,
                        "trace-secret-value",
                        true,
                        "FAILED",
                        "local-echo-primary",
                        "local-echo",
                        "wp5-cost-aware",
                        "default",
                        "JSON",
                        true,
                        123,
                        45,
                        new BigDecimal("0.00012345"),
                        875L,
                        "MODEL_TIMEOUT",
                        "provider token=secret-value rawPrompt timed out",
                        "wp5-test-design",
                        Instant.parse("2026-05-30T00:00:00Z")
                ),
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportModelObservationPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("modelObservationPolicy,policyVersion,,wp5-model-observation-policy-v1")
                .contains("modelObservationPolicy,observationMode,,ROUTING_COST_LATENCY_AGGREGATE")
                .contains("modelObservationPolicy,wp2InvocationReferenceTracked,,true,,success")
                .contains("modelObservationPolicy,traceIdTracked,,true")
                .contains("modelObservationPolicy,jobIdTracked,,true")
                .contains("modelObservationPolicy,routingMetadataTracked,,true")
                .contains("modelObservationPolicy,tokenUsageTracked,,true")
                .contains("modelObservationPolicy,latencyTracked,,true")
                .contains("modelObservationPolicy,costTracked,,true")
                .contains("modelObservationPolicy,fallbackTracked,,true")
                .contains("modelObservationPolicy,promptPayloadStored,,false")
                .contains("modelObservationPolicy,payloadPreviewExported,,false")
                .contains("modelObservationPolicy,traceIdValueExported,,false")
                .contains("modelObservationPolicy,jobIdValueExported,,false")
                .contains("modelObservationPolicy,invocationIdValueExported,,false")
                .contains("modelObservationPolicy,providerErrorTextExported,,false")
                .contains("modelObservationPolicy,actorServiceExported,,false")
                .contains("modelObservationPolicy,metric,routingMetadataFieldCount,5,,info")
                .contains("modelObservationPolicy,metric,tokenUsageMetricCount,2,,info")
                .contains("modelObservationPolicy,metric,costMetricCount,1,,info")
                .contains("modelObservationPolicy,metric,latencyMetricCount,1,,info")
                .contains("modelObservationPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain(invocationId.toString())
                .doesNotContain(jobId.toString())
                .doesNotContain("trace-secret-value")
                .doesNotContain("rawPrompt")
                .doesNotContain("requestPreview")
                .doesNotContain("responsePreview")
                .doesNotContain("provider token")
                .doesNotContain("wp5-test-design");
    }

    @Test
    void appendsGenerationOrchestrationPolicyRowsWithoutEventDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "生成编排策略报告 token=secret-value",
                "FAILED",
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
                "eventId=evt-secret queueMessageBody token=secret-value 运行超时",
                "auditor",
                "idempotency-key-secret",
                "digest",
                null,
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );

        TestDesignTaskReportGenerationOrchestrationPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"), properties(180, false, true));

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("generationOrchestrationPolicy,policyVersion,,wp5-generation-orchestration-policy-v1")
                .contains("generationOrchestrationPolicy,orchestrationMode,,ASYNC_EVENT_CONDITIONAL_CLAIM,,success")
                .contains("generationOrchestrationPolicy,asyncGenerationEnabled,,true,,success")
                .contains("generationOrchestrationPolicy,conditionalRunClaimSupported,,true,,success")
                .contains("generationOrchestrationPolicy,idempotentCreateReplaySupported,,true,,success")
                .contains("generationOrchestrationPolicy,duplicateEventReplaySafe,,true,,success")
                .contains("generationOrchestrationPolicy,eventRecoveryEnabled,,true,,success")
                .contains("generationOrchestrationPolicy,queuedEventReplaySupported,,true,,success")
                .contains("generationOrchestrationPolicy,runningTimeoutRecoveryEnabled,,true,,success")
                .contains("generationOrchestrationPolicy,explicitRetryRequiredAfterTimeout,,true,,success")
                .contains("generationOrchestrationPolicy,manualTaskRetrySupported,,true,,success")
                .contains("generationOrchestrationPolicy,manualQueuedEventReplayReady,,true,,success")
                .contains("generationOrchestrationPolicy,queueLagMetricReady,,true,,success")
                .contains("generationOrchestrationPolicy,timeoutAlertReady,,true,,success")
                .contains("generationOrchestrationPolicy,multiInstanceLoadTestEvidenceReady,,true,,success")
                .contains("generationOrchestrationPolicy,eventPayloadExported,,false")
                .contains("generationOrchestrationPolicy,eventIdentifierListExported,,false")
                .contains("generationOrchestrationPolicy,queueMessageBodyExported,,false")
                .contains("generationOrchestrationPolicy,recoveryDetailRowsExported,,false")
                .contains("generationOrchestrationPolicy,metric,effectiveRecoveryBatchSize,100,,info")
                .contains("generationOrchestrationPolicy,metric,runningTimeoutSeconds,600,,info")
                .contains("generationOrchestrationPolicy,metric,queueLagWarningSeconds,120,,info")
                .contains("generationOrchestrationPolicy,metric,queuedTaskCount,0,,neutral")
                .contains("generationOrchestrationPolicy,metric,runningTaskCount,0,,neutral")
                .contains("generationOrchestrationPolicy,metric,oldestQueuedAgeSeconds,0,,neutral")
                .contains("generationOrchestrationPolicy,metric,staleRunningTaskCount,0,,neutral")
                .contains("generationOrchestrationPolicy,metric,queuedStatusSignal,0,,neutral")
                .contains("generationOrchestrationPolicy,metric,runningStatusSignal,0,,neutral")
                .contains("generationOrchestrationPolicy,metric,timeoutFailureSignal,1,,warning")
                .contains("generationOrchestrationPolicy,queueLagWarning,,false,,success")
                .contains("generationOrchestrationPolicy,timeoutWarning,,false,,success")
                .contains("generationOrchestrationPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("evt-secret")
                .doesNotContain("queueMessageBody token")
                .doesNotContain("idempotency-key-secret")
                .doesNotContain("运行超时");
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
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
    void appendsReleaseReadinessPolicyRowsWithoutApprovalOrCandidateEvidence() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "发布准出报告 token=secret-value",
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
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of("releaseReadinessPolicy", Map.of(
                        "candidateEvidence", "candidate-secret-id",
                        "approvalNotes", "approval note should not appear",
                        "thresholdRuleDetails", "threshold rule should not appear"
                )),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );
        TestDesignQualityReadinessResponse readiness = new TestDesignQualityReadinessResponse(
                "BLOCKED",
                2L,
                1L,
                List.of()
        );

        TestDesignTaskReportReleaseReadinessPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"), readiness);

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("releaseReadinessPolicy,policyVersion,,wp5-release-readiness-policy-v1")
                .contains("releaseReadinessPolicy,decisionMode,,ADVISORY_QUALITY_GATE,,warning")
                .contains("releaseReadinessPolicy,thresholdSource,,DEPLOY_CONFIG")
                .contains("releaseReadinessPolicy,qualityThresholdEvaluated,,true,,success")
                .contains("releaseReadinessPolicy,advisoryOnly,,true,,warning")
                .contains("releaseReadinessPolicy,publishBlockingEnabled,,false,,warning")
                .contains("releaseReadinessPolicy,manualApprovalRequired,,true")
                .contains("releaseReadinessPolicy,approvalWorkflowReady,,false,,warning")
                .contains("releaseReadinessPolicy,autoPublishAllowed,,false,,success")
                .contains("releaseReadinessPolicy,confirmedCandidateRequired,,true,,success")
                .contains("releaseReadinessPolicy,qualityGateOverrideSupported,,false")
                .contains("releaseReadinessPolicy,candidateEvidenceExported,,false")
                .contains("releaseReadinessPolicy,approvalNotesExported,,false")
                .contains("releaseReadinessPolicy,thresholdRuleDetailExported,,false")
                .contains("releaseReadinessPolicy,metric,readinessStatus,BLOCKED,,warning")
                .contains("releaseReadinessPolicy,metric,blockingCount,2,,warning")
                .contains("releaseReadinessPolicy,metric,warningCount,1,,warning")
                .contains("releaseReadinessPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("candidate-secret-id")
                .doesNotContain("approval note should not appear")
                .doesNotContain("threshold rule should not appear");
    }

    @Test
    void appendsAuditChainPolicyRowsWithoutAuditOrTraceDetails() {
        StringBuilder csv = new StringBuilder();
        TestDesignTaskResponse task = new TestDesignTaskResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "project-wp5",
                "审计链报告 token=secret-value",
                "SUCCEEDED",
                List.of(),
                List.of("SMOKE"),
                "wp5-test-design-v1",
                "1.0.0",
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                "local-echo-primary",
                "local-echo",
                0,
                0,
                0,
                0,
                null,
                "auditor",
                null,
                "digest",
                new TestDesignModelObservationResponse(
                        UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                        UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                        "trc_audit_chain_secret",
                        true,
                        "SUCCEEDED",
                        "local-echo-primary",
                        "local-echo",
                        "wp5-route",
                        "default",
                        "JSON",
                        false,
                        10,
                        5,
                        new BigDecimal("0.0001"),
                        50L,
                        null,
                        null,
                        "wp5-test-design",
                        Instant.parse("2026-05-30T00:00:00Z")
                ),
                TestDesignContextAssemblyPolicy.response(),
                TestDesignContextPolicyGovernance.response(),
                TestDesignContextPolicyOperations.response(),
                null,
                TestDesignScopePolicy.response(),
                TestDesignEvaluationCorpusPolicy.response(),
                TestDesignReleaseReadinessPolicy.response(),
                TestDesignAuditChainPolicy.response(),
                TestDesignModelObservationPolicy.response(),
                archivePolicy(),
                TestDesignReportManifestPolicy.response(),
                Map.of("auditChainPolicy", Map.of(
                        "auditLogIds", List.of("audit-log-secret"),
                        "candidateIds", List.of("candidate-secret-id"),
                        "traceIds", List.of("trc_secret"),
                        "sourceRef", "wp5:secret-source-ref",
                        "assetCaseId", "asset-secret-id"
                )),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );
        UUID candidateId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        List<TestDesignReviewRecord> reviewRecords = List.of(new TestDesignReviewRecord(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                candidateId,
                task.id(),
                "project-wp5",
                "UPDATE",
                "GENERATED",
                "EDITED",
                "reviewer",
                "review comment token=secret-value",
                "{}",
                Instant.parse("2026-05-30T00:00:00Z")
        ));
        List<TestDesignPublishRecord> publishRecords = List.of(
                publishRecord(task.id(), candidateId, UUID.fromString("44444444-4444-4444-8444-444444444444"),
                        "CREATE", "SUCCEEDED", null),
                publishRecord(task.id(), UUID.fromString("55555555-5555-4555-8555-555555555555"), null,
                        "CREATE", "FAILED", "trace trc_secret sourceRef wp5-secret should stay internal")
        );

        TestDesignTaskReportAuditChainPolicyRows.appendRows(
                csv, task, Instant.parse("2026-05-30T00:00:00Z"), reviewRecords, publishRecords);

        String report = csv.toString();
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety(report));
        org.assertj.core.api.Assertions.assertThat(report)
                .contains("auditChainPolicy,policyVersion,,wp5-audit-chain-policy-v1")
                .contains("auditChainPolicy,chainMode,,WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT,,warning")
                .contains("auditChainPolicy,eventSource,,TASK_REVIEW_PUBLISH_MODEL_REFERENCES")
                .contains("auditChainPolicy,wp1AuditEventWritten,,true,,success")
                .contains("auditChainPolicy,wp2InvocationReferenceTracked,,true,,success")
                .contains("auditChainPolicy,wp3PublishReferenceTracked,,true,,success")
                .contains("auditChainPolicy,wp5DomainEventsTracked,,true,,success")
                .contains("auditChainPolicy,projectScopeRequired,,true,,success")
                .contains("auditChainPolicy,traceSignalTracked,,true,,success")
                .contains("auditChainPolicy,crossWpAuditDashboardReady,,false,,warning")
                .contains("auditChainPolicy,auditOutboxReplayDashboardReady,,false,,warning")
                .contains("auditChainPolicy,auditEventDetailExported,,false")
                .contains("auditChainPolicy,candidateIdentifierListExported,,false")
                .contains("auditChainPolicy,platformAuditIdentifierExported,,false")
                .contains("auditChainPolicy,traceIdValueExported,,false")
                .contains("auditChainPolicy,modelInvocationIdValueExported,,false")
                .contains("auditChainPolicy,publishIdentifierValueExported,,false")
                .contains("auditChainPolicy,metric,taskEventCount,1,,info")
                .contains("auditChainPolicy,metric,reviewEventCount,1,,info")
                .contains("auditChainPolicy,metric,publishEventCount,2,,info")
                .contains("auditChainPolicy,metric,noteCoverageCount,2,,info")
                .contains("auditChainPolicy,aggregateOnly,,true,,success")
                .doesNotContain("secret-value")
                .doesNotContain("audit-log-secret")
                .doesNotContain("candidate-secret-id")
                .doesNotContain("trc_secret")
                .doesNotContain("wp5-secret")
                .doesNotContain("secret-source-ref")
                .doesNotContain("asset-secret-id")
                .doesNotContain(candidateId.toString());
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
                120,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                0.86D,
                0.90D,
                true,
                50,
                reportArchiveRetentionDays,
                externalSharingAllowed,
                approvalRequired
        );
    }

    private static TestDesignArchivePolicyResponse archivePolicy() {
        return TestDesignArchivePolicy.response(properties(180, false, true));
    }

    private static TestDesignReportManifestPolicyResponse reportManifestPolicy() {
        return TestDesignReportManifestPolicy.response();
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
