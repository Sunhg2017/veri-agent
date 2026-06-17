package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.asset.application.AssetCrossWpReportEvidenceService;
import com.songhg.veri.agent.asset.application.command.AssetReportEvidenceQuery;
import com.songhg.veri.agent.asset.application.view.AssetReportEvidenceResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunExportResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.command.TestDataReportEvidenceQuery;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataReportEvidenceResponse;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpReportEvidenceService;
import com.songhg.veri.agent.testdesign.application.command.TestDesignReportEvidenceQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportEvidenceResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * Builds WP10 aggregate-only evidence manifests from sanitized WP9 refs and cross-WP application contracts.
 */
final class ReportEvidenceAssembler {

    private static final Pattern UNSAFE_SUMMARY_KEY_PATTERN =
            Pattern.compile("(?i).*(authorization|cookie|password|passwd|secret|token|credential).*");
    private static final int MAX_REPORT_REF_COUNT = 100;
    private static final String WP8_EVIDENCE_SCHEMA_VERSION = "wp8-report-evidence-v1";
    private static final String WP3_EVIDENCE_SCHEMA_VERSION = "wp3-report-evidence-v1";
    private static final String WP5_EVIDENCE_SCHEMA_VERSION = "wp5-report-evidence-v1";

    private final TestDataCrossWpReferenceService testDataService;
    private final AssetCrossWpReportEvidenceService assetEvidenceService;
    private final TestDesignCrossWpReportEvidenceService testDesignEvidenceService;
    private final ReportingProperties properties;
    private final ReportingJsonSupport jsonSupport;

    ReportEvidenceAssembler(
            ObjectProvider<TestDataCrossWpReferenceService> testDataServices,
            ObjectProvider<AssetCrossWpReportEvidenceService> assetEvidenceServices,
            ObjectProvider<TestDesignCrossWpReportEvidenceService> testDesignEvidenceServices,
            ReportingProperties properties,
            ReportingJsonSupport jsonSupport
    ) {
        this.testDataService = testDataServices.getIfAvailable();
        this.assetEvidenceService = assetEvidenceServices.getIfAvailable();
        this.testDesignEvidenceService = testDesignEvidenceServices.getIfAvailable();
        this.properties = properties;
        this.jsonSupport = jsonSupport;
    }

    /**
     * Extracts stable cross-WP UUID refs from WP9 sanitized node summaries using an explicit allowlist.
     */
    EvidenceRefs evidenceRefs(List<ExecutionNodeRunResponse> nodes) {
        Wp8EvidenceRefs wp8Refs = wp8EvidenceRefs(nodes);
        Wp3EvidenceRefs wp3Refs = wp3EvidenceRefs(nodes);
        Wp5EvidenceRefs wp5Refs = wp5EvidenceRefs(nodes);
        return new EvidenceRefs(wp8Refs, wp3Refs, wp5Refs);
    }

    /**
     * Builds manifest rows in source order: WP9 nodes first, then WP8/WP3/WP5 aggregate evidence until the configured
     * limit is reached. Cross-WP refs are still validated even when later manifests are truncated by the WP10 limit.
     */
    List<ReportEvidenceManifest> evidenceManifests(
            UUID reportId,
            String projectId,
            ExecutionRunExportResponse export,
            EvidenceRefs evidenceRefs,
            Instant now
    ) {
        List<ExecutionNodeRunResponse> nodes = export.run().nodes();
        int maxItems = properties.effectiveMaxEvidenceItems();
        int wp9Items = Math.min(nodes.size(), maxItems);
        List<ReportEvidenceManifest> manifests = new ArrayList<>(maxItems);
        for (int index = 0; index < wp9Items; index++) {
            manifests.add(wp9Manifest(reportId, export, nodes.get(index), index, manifestCreatedAt(now, index)));
        }
        if (!evidenceRefs.wp8Refs().empty()) {
            TestDataReportEvidenceResponse wp8Evidence = wp8ReportEvidence(projectId, reportId, evidenceRefs.wp8Refs());
            appendWp8EvidenceManifests(reportId, wp8Evidence, manifests, maxItems, now);
        }
        if (!evidenceRefs.wp3Refs().empty()) {
            AssetReportEvidenceResponse wp3Evidence = wp3ReportEvidence(projectId, reportId, evidenceRefs.wp3Refs());
            appendWp3EvidenceManifests(reportId, wp3Evidence, manifests, maxItems, now);
        }
        if (!evidenceRefs.wp5Refs().empty()) {
            TestDesignReportEvidenceResponse wp5Evidence = wp5ReportEvidence(projectId, reportId, evidenceRefs.wp5Refs());
            appendWp5EvidenceManifests(reportId, wp5Evidence, manifests, maxItems, now);
        }
        return manifests;
    }

    private ReportEvidenceManifest wp9Manifest(
            UUID reportId,
            ExecutionRunExportResponse export,
            ExecutionNodeRunResponse node,
            int index,
            Instant now
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeIndex", index);
        summary.put("nodeKey", safeEvidenceText(node.nodeKey(), 128));
        summary.put("nodeType", safeEvidenceText(node.nodeType(), 64));
        summary.put("status", node.status());
        summary.put("attempt", node.attempt());
        summary.put("runnerType", safeEvidenceText(node.runnerType(), 64));
        summary.put("errorCode", safeEvidenceText(node.errorCode(), 64));
        summary.put("durationMillis", durationMillis(node.startedAt(), node.finishedAt()));
        summary.put("resultSummaryKeyCount", node.resultSummary() == null ? 0 : node.resultSummary().size());

        Map<String, Object> redactionFlags = new LinkedHashMap<>();
        redactionFlags.put("sourceWp9ExportSanitized", true);
        redactionFlags.put("summaryValuesStored", false);
        redactionFlags.put("externalRunIdStored", false);
        redactionFlags.put("errorSummaryStored", false);
        redactionFlags.put("rawRunnerArtifactStored", false);
        redactionFlags.put("requestResponseBodyStored", false);
        redactionFlags.put("secretPlaintextStored", false);
        redactionFlags.put("unsafeSummaryKeysFiltered", true);

        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("exportSchemaVersion", export.schemaVersion());
        digestSource.put("runId", export.run().id());
        digestSource.put("nodeRunId", node.id());
        digestSource.put("planNodeId", node.planNodeId());
        digestSource.put("nodeKey", node.nodeKey());
        digestSource.put("nodeType", node.nodeType());
        digestSource.put("status", node.status());
        digestSource.put("attempt", node.attempt());
        digestSource.put("runnerType", node.runnerType());
        digestSource.put("errorCode", node.errorCode());
        digestSource.put("summaryKeys", summaryKeys(node));

        return new ReportEvidenceManifest(
                UUID.randomUUID(),
                reportId,
                "WP9",
                "EXECUTION_NODE",
                SensitiveTextSanitizer.sha256Hex(jsonSupport.json(digestSource)),
                export.schemaVersion(),
                jsonSupport.json(summaryKeys(node)),
                jsonSupport.json(redactionFlags),
                jsonSupport.json(summary),
                now
        );
    }

    private TestDataReportEvidenceResponse wp8ReportEvidence(String projectId, UUID reportId, Wp8EvidenceRefs refs) {
        if (testDataService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_WP8_EVIDENCE_SERVICE_UNAVAILABLE");
        }
        return testDataService.reportEvidence(new TestDataReportEvidenceQuery(
                projectId,
                reportId.toString(),
                refs.dataSetRefs(),
                refs.accountLeaseRefs(),
                refs.cleanupTaskRefs()
        ));
    }

    private AssetReportEvidenceResponse wp3ReportEvidence(String projectId, UUID reportId, Wp3EvidenceRefs refs) {
        if (assetEvidenceService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_WP3_EVIDENCE_SERVICE_UNAVAILABLE");
        }
        return assetEvidenceService.reportEvidence(new AssetReportEvidenceQuery(
                projectId,
                reportId.toString(),
                refs.requirementRefs(),
                refs.apiRefs(),
                refs.pageRefs(),
                refs.businessFlowRefs(),
                refs.testCaseRefs()
        ));
    }

    private TestDesignReportEvidenceResponse wp5ReportEvidence(String projectId, UUID reportId, Wp5EvidenceRefs refs) {
        if (testDesignEvidenceService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_WP5_EVIDENCE_SERVICE_UNAVAILABLE");
        }
        return testDesignEvidenceService.reportEvidence(new TestDesignReportEvidenceQuery(
                projectId,
                reportId.toString(),
                refs.taskRefs(),
                refs.candidateRefs()
        ));
    }

    private Wp8EvidenceRefs wp8EvidenceRefs(List<ExecutionNodeRunResponse> nodes) {
        LinkedHashSet<UUID> dataSetRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> accountLeaseRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> cleanupTaskRefs = new LinkedHashSet<>();
        boolean truncated = false;
        for (ExecutionNodeRunResponse node : nodes) {
            Map<String, Object> summary = node.resultSummary();
            if (summary == null || summary.isEmpty()) {
                continue;
            }
            truncated |= collectUuidRefs(summary.get("dataSetRef"), dataSetRefs);
            truncated |= collectUuidRefs(summary.get("dataSetRefs"), dataSetRefs);
            truncated |= collectUuidRefs(summary.get("testDataSetRef"), dataSetRefs);
            truncated |= collectUuidRefs(summary.get("testDataSetRefs"), dataSetRefs);
            truncated |= collectUuidRefs(summary.get("accountLeaseRef"), accountLeaseRefs);
            truncated |= collectUuidRefs(summary.get("accountLeaseRefs"), accountLeaseRefs);
            truncated |= collectUuidRefs(summary.get("cleanupTaskRef"), cleanupTaskRefs);
            truncated |= collectUuidRefs(summary.get("cleanupTaskRefs"), cleanupTaskRefs);
            truncated |= collectUuidRefs(summary.get("testDataCleanupTaskRef"), cleanupTaskRefs);
            truncated |= collectUuidRefs(summary.get("testDataCleanupTaskRefs"), cleanupTaskRefs);
        }
        return new Wp8EvidenceRefs(
                dataSetRefs.stream().toList(),
                accountLeaseRefs.stream().toList(),
                cleanupTaskRefs.stream().toList(),
                truncated
        );
    }

    private Wp3EvidenceRefs wp3EvidenceRefs(List<ExecutionNodeRunResponse> nodes) {
        LinkedHashSet<UUID> requirementRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> apiRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> pageRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> businessFlowRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> testCaseRefs = new LinkedHashSet<>();
        boolean truncated = false;
        for (ExecutionNodeRunResponse node : nodes) {
            Map<String, Object> summary = node.resultSummary();
            if (summary == null || summary.isEmpty()) {
                continue;
            }
            truncated |= collectUuidRefs(summary.get("requirementRef"), requirementRefs);
            truncated |= collectUuidRefs(summary.get("requirementRefs"), requirementRefs);
            truncated |= collectUuidRefs(summary.get("wp3RequirementRef"), requirementRefs);
            truncated |= collectUuidRefs(summary.get("wp3RequirementRefs"), requirementRefs);
            truncated |= collectUuidRefs(summary.get("apiRef"), apiRefs);
            truncated |= collectUuidRefs(summary.get("apiRefs"), apiRefs);
            truncated |= collectUuidRefs(summary.get("wp3ApiRef"), apiRefs);
            truncated |= collectUuidRefs(summary.get("wp3ApiRefs"), apiRefs);
            truncated |= collectUuidRefs(summary.get("pageRef"), pageRefs);
            truncated |= collectUuidRefs(summary.get("pageRefs"), pageRefs);
            truncated |= collectUuidRefs(summary.get("businessFlowRef"), businessFlowRefs);
            truncated |= collectUuidRefs(summary.get("businessFlowRefs"), businessFlowRefs);
            truncated |= collectUuidRefs(summary.get("testCaseRef"), testCaseRefs);
            truncated |= collectUuidRefs(summary.get("testCaseRefs"), testCaseRefs);
            truncated |= collectUuidRefs(summary.get("wp3TestCaseRef"), testCaseRefs);
            truncated |= collectUuidRefs(summary.get("wp3TestCaseRefs"), testCaseRefs);
        }
        return new Wp3EvidenceRefs(
                requirementRefs.stream().toList(),
                apiRefs.stream().toList(),
                pageRefs.stream().toList(),
                businessFlowRefs.stream().toList(),
                testCaseRefs.stream().toList(),
                truncated
        );
    }

    private Wp5EvidenceRefs wp5EvidenceRefs(List<ExecutionNodeRunResponse> nodes) {
        LinkedHashSet<UUID> taskRefs = new LinkedHashSet<>();
        LinkedHashSet<UUID> candidateRefs = new LinkedHashSet<>();
        boolean truncated = false;
        for (ExecutionNodeRunResponse node : nodes) {
            Map<String, Object> summary = node.resultSummary();
            if (summary == null || summary.isEmpty()) {
                continue;
            }
            truncated |= collectUuidRefs(summary.get("testDesignTaskRef"), taskRefs);
            truncated |= collectUuidRefs(summary.get("testDesignTaskRefs"), taskRefs);
            truncated |= collectUuidRefs(summary.get("wp5TaskRef"), taskRefs);
            truncated |= collectUuidRefs(summary.get("wp5TaskRefs"), taskRefs);
            truncated |= collectUuidRefs(summary.get("testDesignCandidateRef"), candidateRefs);
            truncated |= collectUuidRefs(summary.get("testDesignCandidateRefs"), candidateRefs);
            truncated |= collectUuidRefs(summary.get("wp5CandidateRef"), candidateRefs);
            truncated |= collectUuidRefs(summary.get("wp5CandidateRefs"), candidateRefs);
        }
        return new Wp5EvidenceRefs(taskRefs.stream().toList(), candidateRefs.stream().toList(), truncated);
    }

    private boolean collectUuidRefs(Object value, LinkedHashSet<UUID> refs) {
        if (value == null) {
            return false;
        }
        if (value instanceof Iterable<?> values) {
            boolean truncated = false;
            for (Object item : values) {
                truncated |= collectUuidRefs(item, refs);
            }
            return truncated;
        }
        if (value instanceof Object[] values) {
            boolean truncated = false;
            for (Object item : values) {
                truncated |= collectUuidRefs(item, refs);
            }
            return truncated;
        }
        UUID ref = uuid(value);
        if (ref == null || refs.contains(ref)) {
            return false;
        }
        if (refs.size() >= MAX_REPORT_REF_COUNT) {
            return true;
        }
        refs.add(ref);
        return false;
    }

    private UUID uuid(Object value) {
        if (value instanceof UUID ref) {
            return ref;
        }
        if (!StringUtils.hasText(value == null ? null : String.valueOf(value))) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void appendWp8EvidenceManifests(
            UUID reportId,
            TestDataReportEvidenceResponse response,
            List<ReportEvidenceManifest> manifests,
            int maxItems,
            Instant now
    ) {
        for (TestDataReportEvidenceResponse.DataSetEvidence dataSet : response.dataSets()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp8DataSetManifest(reportId, dataSet, manifestCreatedAt(now, manifests.size())));
        }
        for (TestDataReportEvidenceResponse.AccountLeaseEvidence lease : response.accountLeases()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp8AccountLeaseManifest(reportId, lease, manifestCreatedAt(now, manifests.size())));
        }
        for (TestDataReportEvidenceResponse.CleanupTaskEvidence task : response.cleanupTasks()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp8CleanupTaskManifest(reportId, task, manifestCreatedAt(now, manifests.size())));
        }
    }

    private void appendWp3EvidenceManifests(
            UUID reportId,
            AssetReportEvidenceResponse response,
            List<ReportEvidenceManifest> manifests,
            int maxItems,
            Instant now
    ) {
        for (AssetReportEvidenceResponse.RequirementEvidence requirement : response.requirements()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp3RequirementManifest(reportId, requirement, manifestCreatedAt(now, manifests.size())));
        }
        for (AssetReportEvidenceResponse.ApiEvidence api : response.apis()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp3ApiManifest(reportId, api, manifestCreatedAt(now, manifests.size())));
        }
        for (AssetReportEvidenceResponse.PageEvidence page : response.pages()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp3PageManifest(reportId, page, manifestCreatedAt(now, manifests.size())));
        }
        for (AssetReportEvidenceResponse.BusinessFlowEvidence flow : response.businessFlows()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp3BusinessFlowManifest(reportId, flow, manifestCreatedAt(now, manifests.size())));
        }
        for (AssetReportEvidenceResponse.TestCaseEvidence testCase : response.testCases()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp3TestCaseManifest(reportId, testCase, manifestCreatedAt(now, manifests.size())));
        }
    }

    private void appendWp5EvidenceManifests(
            UUID reportId,
            TestDesignReportEvidenceResponse response,
            List<ReportEvidenceManifest> manifests,
            int maxItems,
            Instant now
    ) {
        for (TestDesignReportEvidenceResponse.TaskEvidence task : response.tasks()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp5TaskManifest(reportId, task, manifestCreatedAt(now, manifests.size())));
        }
        for (TestDesignReportEvidenceResponse.CandidateEvidence candidate : response.candidates()) {
            if (manifests.size() >= maxItems) {
                return;
            }
            manifests.add(wp5CandidateManifest(reportId, candidate, manifestCreatedAt(now, manifests.size())));
        }
    }

    private ReportEvidenceManifest wp8DataSetManifest(
            UUID reportId,
            TestDataReportEvidenceResponse.DataSetEvidence dataSet,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dataSetRefDigest", sourceRefDigest("WP8", "TEST_DATA_SET", dataSet.dataSetRef()));
        summary.put("applicationId", safeEvidenceText(dataSet.applicationId(), 64));
        summary.put("environmentId", safeEvidenceText(dataSet.environmentId(), 64));
        summary.put("code", safeEvidenceText(dataSet.code(), 96));
        summary.put("status", safeEvidenceText(dataSet.status(), 32));
        summary.put("sensitivityLevel", safeEvidenceText(dataSet.sensitivityLevel(), 32));
        summary.put("schemaFieldCount", dataSet.schemaFieldCount());
        summary.put("recordCount", dataSet.recordCount());
        summary.put("cleanupPolicyDigest", safeEvidenceText(dataSet.cleanupPolicyDigest(), 128));
        summary.put("sourceRefDigest", safeEvidenceText(dataSet.sourceRefDigest(), 128));
        return wp8Manifest(reportId, "TEST_DATA_SET", dataSet.dataSetRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp8AccountLeaseManifest(
            UUID reportId,
            TestDataReportEvidenceResponse.AccountLeaseEvidence lease,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("accountLeaseRefDigest", sourceRefDigest("WP8", "ACCOUNT_LEASE", lease.accountLeaseRef()));
        summary.put("status", safeEvidenceText(lease.status(), 32));
        summary.put("holderType", safeEvidenceText(lease.holderType(), 64));
        summary.put("holderRefDigest", digestNullable(lease.holderRef()));
        summary.put("expiresAt", stringInstant(lease.expiresAt()));
        summary.put("releasedAt", stringInstant(lease.releasedAt()));
        appendAccountSummary(summary, lease.account());
        return wp8Manifest(reportId, "ACCOUNT_LEASE", lease.accountLeaseRef(), summary, createdAt);
    }

    private void appendAccountSummary(Map<String, Object> summary, TestDataCrossWpAccountSummary account) {
        if (account == null) {
            summary.put("accountPresent", false);
            return;
        }
        summary.put("accountPresent", true);
        summary.put("accountRefDigest", digestNullable(account.accountRef()));
        summary.put("accountPoolRefDigest", digestNullable(account.accountPoolRef()));
        summary.put("accountProjectId", safeEvidenceText(account.projectId(), 64));
        summary.put("accountStatus", safeEvidenceText(account.status(), 32));
        summary.put("accountRoleTagCount", account.roleTags() == null ? 0 : account.roleTags().size());
        summary.put("accountScopeSummaryKeys", safeSummaryKeys(account.scopeSummary() == null
                ? List.of()
                : account.scopeSummary().keySet().stream().map(String::valueOf).toList()));
        summary.put("secretRefDigest", safeEvidenceText(account.secretRefDigest(), 128));
        summary.put("lastHealthStatus", safeEvidenceText(account.lastHealthStatus(), 64));
    }

    private ReportEvidenceManifest wp8CleanupTaskManifest(
            UUID reportId,
            TestDataReportEvidenceResponse.CleanupTaskEvidence task,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cleanupTaskRefDigest", sourceRefDigest("WP8", "CLEANUP_TASK", task.cleanupTaskRef()));
        summary.put("dataSetRefDigest", sourceRefDigest("WP8", "TEST_DATA_SET", task.dataSetRef()));
        summary.put("taskType", safeEvidenceText(task.taskType(), 64));
        summary.put("status", safeEvidenceText(task.status(), 32));
        summary.put("targetRefDigest", safeEvidenceText(task.targetRefDigest(), 128));
        summary.put("attempt", task.attempt());
        summary.put("resultSummaryDigest", safeEvidenceText(task.resultSummaryDigest(), 128));
        summary.put("resultSummaryKeys", safeSummaryKeys(task.resultSummaryKeys()));
        summary.put("errorCode", safeEvidenceText(task.errorCode(), 64));
        summary.put("errorSummaryDigest", safeEvidenceText(task.errorSummaryDigest(), 128));
        summary.put("traceId", safeEvidenceText(task.traceId(), 96));
        summary.put("startedAt", stringInstant(task.startedAt()));
        summary.put("finishedAt", stringInstant(task.finishedAt()));
        return wp8Manifest(reportId, "CLEANUP_TASK", task.cleanupTaskRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp3RequirementManifest(
            UUID reportId,
            AssetReportEvidenceResponse.RequirementEvidence requirement,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requirementRefDigest", sourceRefDigest("WP3", "REQUIREMENT", requirement.requirementRef()));
        summary.put("status", safeEvidenceText(requirement.status(), 32));
        summary.put("priority", safeEvidenceText(requirement.priority(), 32));
        summary.put("version", requirement.version());
        summary.put("lifecycleStatus", safeEvidenceText(requirement.lifecycleStatus(), 32));
        summary.put("tagCount", requirement.tagCount());
        summary.put("traceLinkCount", requirement.traceLinkCount());
        summary.put("linkedApiCount", requirement.linkedApiCount());
        summary.put("linkedPageCount", requirement.linkedPageCount());
        summary.put("linkedFlowCount", requirement.linkedFlowCount());
        summary.put("linkedCaseCount", requirement.linkedCaseCount());
        summary.put("updatedAt", stringInstant(requirement.updatedAt()));
        return wp3Manifest(reportId, "REQUIREMENT", requirement.requirementRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp3ApiManifest(
            UUID reportId,
            AssetReportEvidenceResponse.ApiEvidence api,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("apiRefDigest", sourceRefDigest("WP3", "API", api.apiRef()));
        summary.put("status", safeEvidenceText(api.status(), 32));
        summary.put("lifecycleStatus", safeEvidenceText(api.lifecycleStatus(), 32));
        summary.put("httpMethod", safeEvidenceText(api.httpMethod(), 16));
        summary.put("versionPresent", api.versionPresent());
        summary.put("traceLinkCount", api.traceLinkCount());
        summary.put("linkedRequirementCount", api.linkedRequirementCount());
        summary.put("linkedCaseCount", api.linkedCaseCount());
        summary.put("updatedAt", stringInstant(api.updatedAt()));
        return wp3Manifest(reportId, "API", api.apiRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp3PageManifest(
            UUID reportId,
            AssetReportEvidenceResponse.PageEvidence page,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pageRefDigest", sourceRefDigest("WP3", "PAGE", page.pageRef()));
        summary.put("status", safeEvidenceText(page.status(), 32));
        summary.put("lifecycleStatus", safeEvidenceText(page.lifecycleStatus(), 32));
        summary.put("sourceVersionPresent", page.sourceVersionPresent());
        summary.put("traceLinkCount", page.traceLinkCount());
        summary.put("linkedRequirementCount", page.linkedRequirementCount());
        summary.put("linkedCaseCount", page.linkedCaseCount());
        summary.put("updatedAt", stringInstant(page.updatedAt()));
        return wp3Manifest(reportId, "PAGE", page.pageRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp3BusinessFlowManifest(
            UUID reportId,
            AssetReportEvidenceResponse.BusinessFlowEvidence flow,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("businessFlowRefDigest", sourceRefDigest("WP3", "BUSINESS_FLOW", flow.businessFlowRef()));
        summary.put("status", safeEvidenceText(flow.status(), 32));
        summary.put("priority", safeEvidenceText(flow.priority(), 32));
        summary.put("lifecycleStatus", safeEvidenceText(flow.lifecycleStatus(), 32));
        summary.put("traceLinkCount", flow.traceLinkCount());
        summary.put("linkedRequirementCount", flow.linkedRequirementCount());
        summary.put("linkedCaseCount", flow.linkedCaseCount());
        summary.put("updatedAt", stringInstant(flow.updatedAt()));
        return wp3Manifest(reportId, "BUSINESS_FLOW", flow.businessFlowRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp3TestCaseManifest(
            UUID reportId,
            AssetReportEvidenceResponse.TestCaseEvidence testCase,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("testCaseRefDigest", sourceRefDigest("WP3", "TEST_CASE", testCase.testCaseRef()));
        summary.put("status", safeEvidenceText(testCase.status(), 32));
        summary.put("priority", safeEvidenceText(testCase.priority(), 32));
        summary.put("version", testCase.version());
        summary.put("lifecycleStatus", safeEvidenceText(testCase.lifecycleStatus(), 32));
        summary.put("tagCount", testCase.tagCount());
        summary.put("stepCount", testCase.stepCount());
        summary.put("requirementRefDigest", sourceRefDigest("WP3", "REQUIREMENT", testCase.requirementRef()));
        summary.put("apiRefDigest", sourceRefDigest("WP3", "API", testCase.apiRef()));
        summary.put("traceLinkCount", testCase.traceLinkCount());
        summary.put("updatedAt", stringInstant(testCase.updatedAt()));
        return wp3Manifest(reportId, "TEST_CASE", testCase.testCaseRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp5TaskManifest(
            UUID reportId,
            TestDesignReportEvidenceResponse.TaskEvidence task,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskRefDigest", sourceRefDigest("WP5", "TEST_DESIGN_TASK", task.taskRef()));
        summary.put("status", safeEvidenceText(task.status(), 32));
        summary.put("requirementRefCount", task.requirementRefCount());
        summary.put("coverageTypeCount", task.coverageTypeCount());
        summary.put("totalRequirements", task.totalRequirements());
        summary.put("generatedCount", task.generatedCount());
        summary.put("confirmedCount", task.confirmedCount());
        summary.put("publishedCount", task.publishedCount());
        summary.put("modelInvocationPresent", task.modelInvocationPresent());
        summary.put("requestDigest", safeEvidenceText(task.requestDigest(), 128));
        summary.put("inputDigest", safeEvidenceText(task.inputDigest(), 128));
        summary.put("contextSummaryKeyCount", task.contextSummaryKeyCount());
        summary.put("candidateCount", task.candidateCount());
        summary.put("candidateStatusCounts", task.candidateStatusCounts());
        summary.put("reportManifestCount", task.reportManifestCount());
        summary.put("aggregateReportManifestCount", task.aggregateReportManifestCount());
        summary.put("latestReportManifestStatus", safeEvidenceText(task.latestReportManifestStatus(), 32));
        summary.put("latestReportManifestContentDigest", safeEvidenceText(task.latestReportManifestContentDigest(), 128));
        summary.put("latestReportManifestSchemaVersion", safeEvidenceText(task.latestReportManifestSchemaVersion(), 64));
        summary.put("updatedAt", stringInstant(task.updatedAt()));
        return wp5Manifest(reportId, "TEST_DESIGN_TASK", task.taskRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp5CandidateManifest(
            UUID reportId,
            TestDesignReportEvidenceResponse.CandidateEvidence candidate,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("candidateRefDigest", sourceRefDigest("WP5", "TEST_DESIGN_CANDIDATE", candidate.candidateRef()));
        summary.put("taskRefDigest", sourceRefDigest("WP5", "TEST_DESIGN_TASK", candidate.taskRef()));
        summary.put("requirementRefDigest", sourceRefDigest("WP3", "REQUIREMENT", candidate.requirementRef()));
        summary.put("apiRefDigest", sourceRefDigest("WP3", "API", candidate.apiRef()));
        summary.put("assetCaseRefDigest", sourceRefDigest("WP3", "TEST_CASE", candidate.assetCaseRef()));
        summary.put("status", safeEvidenceText(candidate.status(), 32));
        summary.put("coverageType", safeEvidenceText(candidate.coverageType(), 64));
        summary.put("priority", safeEvidenceText(candidate.priority(), 32));
        summary.put("confidence", candidate.confidence());
        summary.put("modelInvocationPresent", candidate.modelInvocationPresent());
        summary.put("confirmed", candidate.confirmed());
        summary.put("version", candidate.version());
        summary.put("updatedAt", stringInstant(candidate.updatedAt()));
        return wp5Manifest(reportId, "TEST_DESIGN_CANDIDATE", candidate.candidateRef(), summary, createdAt);
    }

    private ReportEvidenceManifest wp8Manifest(
            UUID reportId,
            String sourceType,
            Object sourceRef,
            Map<String, Object> summary,
            Instant createdAt
    ) {
        return manifest(reportId, "WP8", sourceType, sourceRef, WP8_EVIDENCE_SCHEMA_VERSION,
                wp8RedactionFlags(), summary, createdAt);
    }

    private ReportEvidenceManifest wp3Manifest(
            UUID reportId,
            String sourceType,
            Object sourceRef,
            Map<String, Object> summary,
            Instant createdAt
    ) {
        return manifest(reportId, "WP3", sourceType, sourceRef, WP3_EVIDENCE_SCHEMA_VERSION,
                wp3RedactionFlags(), summary, createdAt);
    }

    private ReportEvidenceManifest wp5Manifest(
            UUID reportId,
            String sourceType,
            Object sourceRef,
            Map<String, Object> summary,
            Instant createdAt
    ) {
        return manifest(reportId, "WP5", sourceType, sourceRef, WP5_EVIDENCE_SCHEMA_VERSION,
                wp5RedactionFlags(), summary, createdAt);
    }

    private ReportEvidenceManifest manifest(
            UUID reportId,
            String sourceWp,
            String sourceType,
            Object sourceRef,
            String schemaVersion,
            Map<String, Object> redactionFlags,
            Map<String, Object> summary,
            Instant createdAt
    ) {
        return new ReportEvidenceManifest(
                UUID.randomUUID(),
                reportId,
                sourceWp,
                sourceType,
                sourceRefDigest(sourceWp, sourceType, sourceRef),
                schemaVersion,
                jsonSupport.json(safeSummaryKeys(summary.keySet().stream().toList())),
                jsonSupport.json(redactionFlags),
                jsonSupport.json(summary),
                createdAt
        );
    }

    private Map<String, Object> wp8RedactionFlags() {
        Map<String, Object> redactionFlags = new LinkedHashMap<>();
        redactionFlags.put("sourceWp8ReportEvidenceSanitized", true);
        redactionFlags.put("summaryValuesStored", false);
        redactionFlags.put("rawRecordPayloadStored", false);
        redactionFlags.put("cleanupResultPayloadStored", false);
        redactionFlags.put("targetRefStored", false);
        redactionFlags.put("errorSummaryStored", false);
        redactionFlags.put("accountCredentialStored", false);
        redactionFlags.put("secretPlaintextStored", false);
        redactionFlags.put("secretRefPlaintextStored", false);
        redactionFlags.put("leaseTokenStored", false);
        redactionFlags.put("crossWpDirectTableReadAllowed", false);
        return redactionFlags;
    }

    private Map<String, Object> wp3RedactionFlags() {
        Map<String, Object> redactionFlags = new LinkedHashMap<>();
        redactionFlags.put("sourceWp3ReportEvidenceSanitized", true);
        redactionFlags.put("summaryValuesStored", false);
        redactionFlags.put("assetBodyStored", false);
        redactionFlags.put("assetIdentifierStored", false);
        redactionFlags.put("traceIdentifierListStored", false);
        redactionFlags.put("requestResponseBodyStored", false);
        redactionFlags.put("secretPlaintextStored", false);
        redactionFlags.put("crossWpDirectTableReadAllowed", false);
        return redactionFlags;
    }

    private Map<String, Object> wp5RedactionFlags() {
        Map<String, Object> redactionFlags = new LinkedHashMap<>();
        redactionFlags.put("sourceWp5ReportEvidenceSanitized", true);
        redactionFlags.put("summaryValuesStored", false);
        redactionFlags.put("candidateBodyStored", false);
        redactionFlags.put("candidateIdentifierStored", false);
        redactionFlags.put("promptStored", false);
        redactionFlags.put("modelPayloadStored", false);
        redactionFlags.put("auditIdentifierListStored", false);
        redactionFlags.put("crossWpDirectTableReadAllowed", false);
        return redactionFlags;
    }

    private List<String> summaryKeys(ExecutionNodeRunResponse node) {
        if (node.resultSummary() == null || node.resultSummary().isEmpty()) {
            return List.of();
        }
        return safeSummaryKeys(node.resultSummary().keySet().stream().toList());
    }

    private List<String> safeSummaryKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .filter(StringUtils::hasText)
                .filter(key -> !UNSAFE_SUMMARY_KEY_PATTERN.matcher(key).matches())
                .map(key -> SensitiveTextSanitizer.boundedText(key, 96))
                .sorted()
                .toList();
    }

    private String sourceRefDigest(String sourceWp, String sourceType, Object sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("sourceWp", sourceWp);
        digestSource.put("sourceType", sourceType);
        digestSource.put("sourceRef", String.valueOf(sourceRef));
        return SensitiveTextSanitizer.sha256Hex(jsonSupport.json(digestSource));
    }

    private String digestNullable(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return SensitiveTextSanitizer.sha256Hex(String.valueOf(value).trim());
    }

    private String safeEvidenceText(String value, int maxLength) {
        return SensitiveTextSanitizer.sanitizedEvidenceText(value, maxLength);
    }

    private Instant manifestCreatedAt(Instant now, int index) {
        return now.plusMillis(index);
    }

    private Long durationMillis(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
    }

    private String stringInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    record EvidenceRefs(
            Wp8EvidenceRefs wp8Refs,
            Wp3EvidenceRefs wp3Refs,
            Wp5EvidenceRefs wp5Refs
    ) {
        int size() {
            return wp8Refs.size() + wp3Refs.size() + wp5Refs.size();
        }
    }

    record Wp8EvidenceRefs(
            List<UUID> dataSetRefs,
            List<UUID> accountLeaseRefs,
            List<UUID> cleanupTaskRefs,
            boolean truncated
    ) {
        boolean empty() {
            return dataSetRefs.isEmpty() && accountLeaseRefs.isEmpty() && cleanupTaskRefs.isEmpty();
        }

        int size() {
            return dataSetRefs.size() + accountLeaseRefs.size() + cleanupTaskRefs.size();
        }
    }

    record Wp3EvidenceRefs(
            List<UUID> requirementRefs,
            List<UUID> apiRefs,
            List<UUID> pageRefs,
            List<UUID> businessFlowRefs,
            List<UUID> testCaseRefs,
            boolean truncated
    ) {
        boolean empty() {
            return requirementRefs.isEmpty()
                    && apiRefs.isEmpty()
                    && pageRefs.isEmpty()
                    && businessFlowRefs.isEmpty()
                    && testCaseRefs.isEmpty();
        }

        int size() {
            return requirementRefs.size()
                    + apiRefs.size()
                    + pageRefs.size()
                    + businessFlowRefs.size()
                    + testCaseRefs.size();
        }
    }

    record Wp5EvidenceRefs(
            List<UUID> taskRefs,
            List<UUID> candidateRefs,
            boolean truncated
    ) {
        boolean empty() {
            return taskRefs.isEmpty() && candidateRefs.isEmpty();
        }

        int size() {
            return taskRefs.size() + candidateRefs.size();
        }
    }
}
