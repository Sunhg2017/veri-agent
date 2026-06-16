package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.ExecutionRunService;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunExportResponse;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.reporting.application.command.GenerateReportCommand;
import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.query.ReportPageRequest;
import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.application.view.ReportDiagnosisResponse;
import com.songhg.veri.agent.reporting.application.view.ReportDetailResponse;
import com.songhg.veri.agent.reporting.application.view.ReportSummaryResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.command.TestDataReportEvidenceQuery;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataReportEvidenceResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReportService {

    private static final Set<String> TERMINAL_SOURCE_RUN_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELED", "TIMEOUT"
    );
    private static final Set<String> FAILURE_NODE_STATUSES = Set.of("FAILED", "BLOCKED", "TIMEOUT");
    private static final Pattern UNSAFE_SUMMARY_KEY_PATTERN =
            Pattern.compile("(?i).*(authorization|cookie|password|passwd|secret|token|credential).*");
    private static final int MAX_WP8_REPORT_REF_COUNT = 100;
    private static final String WP8_EVIDENCE_SCHEMA_VERSION = "wp8-report-evidence-v1";

    private final ReportingRepository repository;
    private final ExecutionRunService executionRunService;
    private final TestDataCrossWpReferenceService testDataService;
    private final ReportingProperties properties;
    private final ReportingActorResolver actorResolver;
    private final ReportingPlatformContextClient contextClient;
    private final ObjectMapper objectMapper;
    private final ReportingJsonSupport jsonSupport;
    private final ReportResponseMapper responseMapper;
    private final RuleFailureClassifier failureClassifier;
    private final ReportDiagnosisContextBuilder diagnosisContextBuilder;
    private final ReportDiagnosisAiInvoker diagnosisAiInvoker;

    public ReportService(
            ReportingRepository repository,
            ExecutionRunService executionRunService,
            ObjectProvider<TestDataCrossWpReferenceService> testDataServices,
            ObjectProvider<ModelInvocationService> modelInvocationServices,
            ReportingProperties properties,
            ReportingActorResolver actorResolver,
            ReportingPlatformContextClient contextClient,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.executionRunService = executionRunService;
        this.testDataService = testDataServices.getIfAvailable();
        this.properties = properties;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
        this.jsonSupport = new ReportingJsonSupport(objectMapper);
        this.responseMapper = new ReportResponseMapper(jsonSupport);
        this.failureClassifier = new RuleFailureClassifier(jsonSupport);
        this.diagnosisContextBuilder = new ReportDiagnosisContextBuilder(properties, jsonSupport);
        this.diagnosisAiInvoker = new ReportDiagnosisAiInvoker(modelInvocationServices, actorResolver);
    }

    /**
     * Creates an aggregate-only WP10 report snapshot from the WP9 sanitized run export contract.
     *
     * <p>The report service deliberately consumes {@link ExecutionRunService#exportRun(UUID)} instead of execution
     * tables so WP10 never bypasses WP9 redaction. Repeated request keys replay the stored snapshot before reading the
     * source run again, keeping retries idempotent and side-effect-light.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ReportDetailResponse generateReport(GenerateReportCommand command) {
        requireEnabled();
        NormalizedGenerateRequest request = normalize(command);
        if (StringUtils.hasText(request.requestKey())) {
            return repository.reportByProjectRunRequestKey(
                            request.projectId(),
                            request.executionRunId(),
                            request.requestKey()
                    )
                    .map(report -> responseMapper.toDetail(
                            report,
                            repository.evidenceManifests(report.id()),
                            repository.latestFailureDiagnosis(report.id()),
                            true
                    ))
                    .orElseGet(() -> createReport(request));
        }
        return createReport(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportSummaryResponse> reports(ReportPageRequest request) {
        ReportQuery query = normalizeQuery(request.toQuery());
        List<ReportSummaryResponse> items = repository.reports(query)
                .stream()
                .map(report -> responseMapper.toSummary(report, false))
                .toList();
        return PageResponse.of(items, query.index(), query.size(), repository.countReports(query));
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse report(UUID id) {
        ReportExecutionReport report = requireReport(id);
        return responseMapper.toDetail(
                report,
                repository.evidenceManifests(report.id()),
                repository.latestFailureDiagnosis(report.id()),
                false
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ReportDetailResponse retryReport(UUID id) {
        requireEnabled();
        ReportExecutionReport current = requireReport(id);
        if (!"FAILED".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_INVALID_STATE");
        }
        NormalizedGenerateRequest request = new NormalizedGenerateRequest(
                current.projectId(),
                current.executionRunId(),
                current.requestKey(),
                "retry"
        );
        ReportBundle bundle = reportFromExport(current.id(), request, Instant.now());
        ReportExecutionReport regenerated = bundle.report();
        repository.updateReport(regenerated);
        repository.replaceEvidenceManifests(regenerated.id(), bundle.evidenceManifests());
        repository.replaceLatestFailureDiagnosis(regenerated.id(), bundle.failureDiagnosis());
        audit(regenerated, "report.generated", "SUCCESS", Map.of(
                "retry", true,
                "schemaVersion", regenerated.schemaVersion(),
                "sourceRunDigest", regenerated.sourceRunDigest(),
                "evidenceCount", bundle.evidenceManifests().size(),
                "diagnosisStatus", bundle.failureDiagnosis().status()
        ));
        return responseMapper.toDetail(
                regenerated,
                bundle.evidenceManifests(),
                Optional.of(bundle.failureDiagnosis()),
                false
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ReportDetailResponse archiveReport(UUID id) {
        ReportExecutionReport current = requireReport(id);
        if ("ARCHIVED".equals(current.status())) {
            return responseMapper.toDetail(
                    current,
                    repository.evidenceManifests(current.id()),
                    repository.latestFailureDiagnosis(current.id()),
                    false
            );
        }
        Instant now = Instant.now();
        ReportExecutionReport archived = new ReportExecutionReport(
                current.id(),
                current.projectId(),
                current.executionRunId(),
                current.requestKey(),
                "ARCHIVED",
                current.schemaVersion(),
                current.sourceRunDigest(),
                current.reportSummaryJson(),
                current.redactionPolicyJson(),
                current.generatedBy(),
                current.generatedAt(),
                current.failedCode(),
                current.failureSummary(),
                TraceContext.getOrCreateTraceId(),
                now,
                current.createdAt(),
                now
        );
        repository.updateReport(archived);
        audit(archived, "report.archived", "SUCCESS", Map.of("status", "ARCHIVED"));
        return responseMapper.toDetail(
                archived,
                repository.evidenceManifests(archived.id()),
                repository.latestFailureDiagnosis(archived.id()),
                false
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ReportDiagnosisResponse diagnoseReport(UUID id) {
        requireDiagnosisEnabled();
        ReportExecutionReport report = requireReport(id);
        if (!"READY".equals(report.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_INVALID_STATE");
        }
        List<ReportEvidenceManifest> evidenceManifests = repository.evidenceManifests(report.id());
        ReportFailureDiagnosis ruleDiagnosis = repository.latestFailureDiagnosis(report.id())
                .filter(diagnosis -> "RULE_READY".equals(diagnosis.status()))
                .orElseGet(() -> failureClassifier.classify(report.id(), evidenceManifests, Instant.now()));
        ReportDiagnosisContextBuilder.DiagnosisContext diagnosisContext =
                diagnosisContextBuilder.build(report, evidenceManifests, ruleDiagnosis);
        audit(report, "report.diagnosis.requested", "REQUESTED", Map.of(
                "status", "AI_RUNNING",
                "contextDigest", diagnosisContext.responseMetadata().getOrDefault("contextDigest", ""),
                "modelPurpose", "WP10_FAILURE_DIAGNOSIS",
                "budgetPolicy", "WP2_CONTROLLED"
        ));
        ReportDiagnosisAiInvoker.DiagnosisInvocationOutcome outcome =
                diagnosisAiInvoker.invoke(report, diagnosisContext);
        ReportFailureDiagnosis diagnosis = aiDiagnosis(report, ruleDiagnosis, diagnosisContext, outcome);
        repository.replaceLatestFailureDiagnosis(report.id(), diagnosis);
        repository.updateReport(withLatestDiagnosisSummary(report, diagnosis));
        audit(report, "report.diagnosis.completed", diagnosis.status(), Map.of(
                "status", diagnosis.status(),
                "errorCode", diagnosis.errorCode() == null ? "" : diagnosis.errorCode(),
                "contextDigest", diagnosisContext.responseMetadata().getOrDefault("contextDigest", ""),
                "modelInvocationDigest", diagnosis.modelInvocationDigest() == null ? "" : diagnosis.modelInvocationDigest()
        ));
        return responseMapper.toDiagnosis(diagnosis);
    }

    @Transactional(readOnly = true)
    public ReportDiagnosisResponse latestDiagnosis(UUID id) {
        ReportExecutionReport report = requireReport(id);
        return repository.latestFailureDiagnosis(report.id())
                .map(responseMapper::toDiagnosis)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_DIAGNOSIS_NOT_FOUND"));
    }

    public String reportProjectScopeId(UUID id) {
        return repository.reportProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_NOT_FOUND"));
    }

    private ReportDetailResponse createReport(NormalizedGenerateRequest request) {
        ReportBundle bundle = reportFromExport(UUID.randomUUID(), request, Instant.now());
        ReportExecutionReport report = bundle.report();
        boolean inserted = repository.insertReportIfAbsent(report);
        if (!inserted && StringUtils.hasText(request.requestKey())) {
            return repository.reportByProjectRunRequestKey(
                            request.projectId(),
                            request.executionRunId(),
                            request.requestKey()
                    )
                    .map(existing -> responseMapper.toDetail(
                            existing,
                            repository.evidenceManifests(existing.id()),
                            repository.latestFailureDiagnosis(existing.id()),
                            true
                    ))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "REPORT_DUPLICATE_REQUEST"));
        }
        repository.replaceEvidenceManifests(report.id(), bundle.evidenceManifests());
        repository.replaceLatestFailureDiagnosis(report.id(), bundle.failureDiagnosis());
        audit(report, "report.generated", "SUCCESS", Map.of(
                "schemaVersion", report.schemaVersion(),
                "sourceRunDigest", report.sourceRunDigest(),
                "evidenceCount", bundle.evidenceManifests().size(),
                "diagnosisStatus", bundle.failureDiagnosis().status()
        ));
        return responseMapper.toDetail(
                report,
                bundle.evidenceManifests(),
                Optional.of(bundle.failureDiagnosis()),
                false
        );
    }

    private ReportBundle reportFromExport(UUID reportId, NormalizedGenerateRequest request, Instant now) {
        String sourceProjectId = executionRunService.runProjectScopeId(request.executionRunId());
        if (!sameProject(request.projectId(), sourceProjectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "REPORT_SOURCE_RUN_NOT_FOUND");
        }
        ExecutionRunExportResponse export = executionRunService.exportRun(request.executionRunId());
        ExecutionRunDetailResponse run = export.run();
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "REPORT_SOURCE_RUN_NOT_FOUND");
        }
        requireSourceReady(run);
        Wp8EvidenceRefs wp8EvidenceRefs = wp8EvidenceRefs(run.nodes());
        List<ReportEvidenceManifest> evidenceManifests = evidenceManifests(reportId, request, export, wp8EvidenceRefs, now);
        ReportFailureDiagnosis failureDiagnosis = failureClassifier.classify(reportId, evidenceManifests, now);
        Map<String, Object> summary = reportSummary(export, request, evidenceManifests, wp8EvidenceRefs,
                failureDiagnosis);
        Map<String, Object> redactionPolicy = redactionPolicy(export.redactionPolicy());
        ReportExecutionReport report = new ReportExecutionReport(
                reportId,
                request.projectId(),
                request.executionRunId(),
                request.requestKey(),
                "READY",
                properties.effectiveSchemaVersion(),
                sourceDigest(export),
                jsonSupport.json(summary),
                jsonSupport.json(redactionPolicy),
                actorResolver.currentActor(),
                now,
                null,
                null,
                TraceContext.getOrCreateTraceId(),
                null,
                now,
                now
        );
        return new ReportBundle(report, evidenceManifests, failureDiagnosis);
    }

    private void requireSourceReady(ExecutionRunDetailResponse run) {
        if (!TERMINAL_SOURCE_RUN_STATUSES.contains(run.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_SOURCE_RUN_NOT_READY");
        }
        boolean handoffReady = run.nodes().stream().anyMatch(this::readyReportHandoffNode);
        if (!handoffReady) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_SOURCE_RUN_NOT_READY");
        }
    }

    private boolean readyReportHandoffNode(ExecutionNodeRunResponse node) {
        Object handoffReady = node.resultSummary() == null ? null : node.resultSummary().get("reportHandoffReady");
        Object rawReportStored = node.resultSummary() == null ? null : node.resultSummary().get("rawReportStored");
        return "REPORT_HANDOFF".equals(node.nodeType())
                && "SUCCEEDED".equals(node.status())
                && Boolean.TRUE.equals(handoffReady)
                && Boolean.FALSE.equals(rawReportStored);
    }

    private Map<String, Object> reportSummary(
            ExecutionRunExportResponse export,
            NormalizedGenerateRequest request,
            List<ReportEvidenceManifest> evidenceManifests,
            Wp8EvidenceRefs wp8EvidenceRefs,
            ReportFailureDiagnosis failureDiagnosis
    ) {
        ExecutionRunDetailResponse run = export.run();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("source", "WP9_RUN_EXPORT");
        summary.put("sourceSchemaVersion", export.schemaVersion());
        summary.put("sourceExportedAt", export.exportedAt());
        summary.put("executionRunId", run.id());
        summary.put("planId", run.planId());
        summary.put("runStatus", run.status());
        summary.put("triggerType", run.triggerType());
        summary.put("attempt", run.attempt());
        summary.put("sourceTraceId", run.traceId());
        summary.put("durationMillis", durationMillis(run));
        summary.put("nodeCount", run.nodes().size());
        summary.put("nodeStatusCounts", export.nodeStatusCounts());
        summary.put("failureBucketCounts", failureBucketCounts(run.nodes()));
        summary.put("reportHandoffReady", true);
        summary.put("rawReportStored", false);
        summary.put("generationReasonPresent", StringUtils.hasText(request.reason()));
        summary.put("evidenceManifestCount", evidenceManifests.size());
        summary.put("evidenceManifestTruncated", estimatedEvidenceCount(run, wp8EvidenceRefs) > evidenceManifests.size());
        summary.put("wp8EvidenceReferenceCount", wp8EvidenceRefs.size());
        summary.put("wp8EvidenceManifestCount", evidenceManifests.stream()
                .filter(manifest -> "WP8".equals(manifest.sourceWp()))
                .count());
        summary.put("wp8EvidenceReferenceTruncated", wp8EvidenceRefs.truncated());
        summary.put("diagnosisStatus", failureDiagnosis.status());
        summary.put("diagnosisRuleVersion", RuleFailureClassifier.RULE_VERSION);
        summary.put("diagnosisPrimaryCategory", primaryCategory(failureDiagnosis));
        summary.put("diagnosisManualReviewRequired", failureDiagnosis.manualReviewRequired());
        summary.put("defectDraftCount", 0);
        summary.put("exportManifestCount", 0);
        return summary;
    }

    private String primaryCategory(ReportFailureDiagnosis failureDiagnosis) {
        Object primaryCategory = jsonSupport.readMap(failureDiagnosis.classificationJson()).get("primaryCategory");
        return primaryCategory == null ? "UNKNOWN" : String.valueOf(primaryCategory);
    }

    private ReportFailureDiagnosis aiDiagnosis(
            ReportExecutionReport report,
            ReportFailureDiagnosis ruleDiagnosis,
            ReportDiagnosisContextBuilder.DiagnosisContext diagnosisContext,
            ReportDiagnosisAiInvoker.DiagnosisInvocationOutcome outcome
    ) {
        Instant now = Instant.now();
        Map<String, Object> previousSummary = jsonSupport.readMap(ruleDiagnosis.diagnosisSummaryJson());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rootCauseCandidates", previousSummary.getOrDefault("rootCauseCandidates", List.of()));
        summary.put("candidateCount", previousSummary.getOrDefault("candidateCount", 0));
        summary.put("aiDiagnosisReady", outcome.ready());
        summary.put("modelInvoked", outcome.ready());
        summary.put("classificationOnly", !outcome.ready());
        summary.put("fallbackFromRule", true);
        summary.put("diagnosisContext", diagnosisContext.responseMetadata());
        summary.put("redactionPolicy", Map.of(
                "aggregateOnly", true,
                "contextDigestOnly", true,
                "evidenceValuesStored", false,
                "rawPromptStored", false,
                "rawResponseStored", false,
                "credentialPlaintextStored", false,
                "modelProviderPayloadStored", false
        ));
        if (outcome.ready()) {
            summary.put("aiSummary", Map.of(
                    "modelOutputStored", false,
                    "manualReviewRequired", true,
                    "modelMetadata", outcome.modelMetadata()
            ));
        } else {
            summary.put("aiFailure", Map.of(
                    "errorCode", outcome.errorCode(),
                    "wp2ErrorCode", outcome.wp2ErrorCode() == null ? "" : outcome.wp2ErrorCode(),
                    "reason", outcome.failureReason()
            ));
        }

        return new ReportFailureDiagnosis(
                UUID.randomUUID(),
                report.id(),
                outcome.ready() ? "AI_READY" : "AI_FAILED",
                ruleDiagnosis.classificationJson(),
                outcome.modelInvocationDigest(),
                ruleDiagnosis.confidence(),
                outcome.ready() || ruleDiagnosis.manualReviewRequired(),
                jsonSupport.json(summary),
                outcome.errorCode(),
                now,
                now
        );
    }

    private Map<String, Object> diagnosisContext(ReportFailureDiagnosis diagnosis) {
        Object context = jsonSupport.readMap(diagnosis.diagnosisSummaryJson()).get("diagnosisContext");
        return context instanceof Map<?, ?> map
                ? map.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()),
                                Map.Entry::getValue,
                                (left, ignored) -> left,
                                LinkedHashMap::new
                        ))
                : Map.of();
    }

    private ReportExecutionReport withLatestDiagnosisSummary(
            ReportExecutionReport report,
            ReportFailureDiagnosis diagnosis
    ) {
        Map<String, Object> summary = new LinkedHashMap<>(jsonSupport.readMap(report.reportSummaryJson()));
        summary.put("diagnosisStatus", diagnosis.status());
        summary.put("diagnosisRuleVersion", RuleFailureClassifier.RULE_VERSION);
        summary.put("diagnosisPrimaryCategory", primaryCategory(diagnosis));
        summary.put("diagnosisManualReviewRequired", diagnosis.manualReviewRequired());
        return new ReportExecutionReport(
                report.id(),
                report.projectId(),
                report.executionRunId(),
                report.requestKey(),
                report.status(),
                report.schemaVersion(),
                report.sourceRunDigest(),
                jsonSupport.json(summary),
                report.redactionPolicyJson(),
                report.generatedBy(),
                report.generatedAt(),
                report.failedCode(),
                report.failureSummary(),
                report.traceId(),
                report.archivedAt(),
                report.createdAt(),
                Instant.now()
        );
    }

    private Map<String, Object> redactionPolicy(Map<String, Object> wp9Policy) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("aggregateOnly", true);
        policy.put("sourceWp9RedactionPolicy", wp9Policy == null ? Map.of() : wp9Policy);
        policy.put("crossWpDirectTableReadAllowed", false);
        policy.put("rawRunnerArtifactStored", false);
        policy.put("stdoutStderrStored", false);
        policy.put("requestResponseBodyStored", false);
        policy.put("secretPlaintextStored", false);
        policy.put("rawPromptStored", false);
        policy.put("rawResponseStored", false);
        policy.put("triggerPayloadStored", false);
        return policy;
    }

    /**
     * Builds WP10 M3 evidence manifests from the already-sanitized WP9 run export and WP8 cross-WP contract.
     *
     * <p>Only node metadata, summary key names, counts and digests are persisted. The node result values, external run
     * IDs, source reference IDs, error summaries and any raw runner payload stay outside WP10 storage.</p>
     */
    private List<ReportEvidenceManifest> evidenceManifests(
            UUID reportId,
            NormalizedGenerateRequest request,
            ExecutionRunExportResponse export,
            Wp8EvidenceRefs wp8EvidenceRefs,
            Instant now
    ) {
        List<ExecutionNodeRunResponse> nodes = export.run().nodes();
        int maxItems = properties.effectiveMaxEvidenceItems();
        int wp9Items = Math.min(nodes.size(), maxItems);
        List<ReportEvidenceManifest> manifests = new ArrayList<>(maxItems);
        for (int index = 0; index < wp9Items; index++) {
            manifests.add(evidenceManifest(reportId, export, nodes.get(index), index, manifestCreatedAt(now, index)));
        }
        if (!wp8EvidenceRefs.empty()) {
            TestDataReportEvidenceResponse wp8Evidence = wp8ReportEvidence(request, reportId, wp8EvidenceRefs);
            appendWp8EvidenceManifests(
                    reportId,
                    wp8Evidence,
                    manifests,
                    maxItems,
                    now
            );
        }
        return manifests;
    }

    private ReportEvidenceManifest evidenceManifest(
            UUID reportId,
            ExecutionRunExportResponse export,
            ExecutionNodeRunResponse node,
            int index,
            Instant now
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeIndex", index);
        summary.put("nodeKey", SensitiveTextSanitizer.sanitizedEvidenceText(node.nodeKey(), 128));
        summary.put("nodeType", SensitiveTextSanitizer.sanitizedEvidenceText(node.nodeType(), 64));
        summary.put("status", node.status());
        summary.put("attempt", node.attempt());
        summary.put("runnerType", SensitiveTextSanitizer.sanitizedEvidenceText(node.runnerType(), 64));
        summary.put("errorCode", SensitiveTextSanitizer.sanitizedEvidenceText(node.errorCode(), 64));
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

    private List<String> summaryKeys(ExecutionNodeRunResponse node) {
        if (node.resultSummary() == null || node.resultSummary().isEmpty()) {
            return List.of();
        }
        return safeSummaryKeys(node.resultSummary().keySet().stream().toList());
    }

    /**
     * Resolves WP8 evidence through the WP8 application contract. WP10 only extracts stable references from the
     * already-sanitized WP9 summary and never reads WP8 data tables directly.
     */
    private TestDataReportEvidenceResponse wp8ReportEvidence(
            NormalizedGenerateRequest request,
            UUID reportId,
            Wp8EvidenceRefs refs
    ) {
        if (testDataService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_WP8_EVIDENCE_SERVICE_UNAVAILABLE");
        }
        return testDataService.reportEvidence(new TestDataReportEvidenceQuery(
                request.projectId(),
                reportId.toString(),
                refs.dataSetRefs(),
                refs.accountLeaseRefs(),
                refs.cleanupTaskRefs()
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
        if (refs.size() >= MAX_WP8_REPORT_REF_COUNT) {
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

    private ReportEvidenceManifest wp8DataSetManifest(
            UUID reportId,
            TestDataReportEvidenceResponse.DataSetEvidence dataSet,
            Instant createdAt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dataSetRefDigest", wp8SourceRefDigest("TEST_DATA_SET", dataSet.dataSetRef()));
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
        summary.put("accountLeaseRefDigest", wp8SourceRefDigest("ACCOUNT_LEASE", lease.accountLeaseRef()));
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
        summary.put("cleanupTaskRefDigest", wp8SourceRefDigest("CLEANUP_TASK", task.cleanupTaskRef()));
        summary.put("dataSetRefDigest", wp8SourceRefDigest("TEST_DATA_SET", task.dataSetRef()));
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

    private ReportEvidenceManifest wp8Manifest(
            UUID reportId,
            String sourceType,
            Object sourceRef,
            Map<String, Object> summary,
            Instant createdAt
    ) {
        return new ReportEvidenceManifest(
                UUID.randomUUID(),
                reportId,
                "WP8",
                sourceType,
                wp8SourceRefDigest(sourceType, sourceRef),
                WP8_EVIDENCE_SCHEMA_VERSION,
                jsonSupport.json(safeSummaryKeys(summary.keySet().stream().toList())),
                jsonSupport.json(wp8RedactionFlags()),
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

    private String wp8SourceRefDigest(String sourceType, Object sourceRef) {
        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("sourceWp", "WP8");
        digestSource.put("sourceType", sourceType);
        digestSource.put("sourceRef", sourceRef == null ? null : String.valueOf(sourceRef));
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

    private int estimatedEvidenceCount(ExecutionRunDetailResponse run, Wp8EvidenceRefs wp8EvidenceRefs) {
        return run.nodes().size() + wp8EvidenceRefs.size();
    }

    private Map<String, Integer> failureBucketCounts(List<ExecutionNodeRunResponse> nodes) {
        return nodes.stream()
                .filter(node -> FAILURE_NODE_STATUSES.contains(node.status()))
                .collect(Collectors.toMap(
                        node -> StringUtils.hasText(node.errorCode()) ? node.errorCode() : node.status(),
                        ignored -> 1,
                        Integer::sum,
                        LinkedHashMap::new
                ));
    }

    private Long durationMillis(ExecutionRunDetailResponse run) {
        return durationMillis(run.startedAt(), run.finishedAt());
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

    private String sourceDigest(ExecutionRunExportResponse export) {
        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("schemaVersion", export.schemaVersion());
        digestSource.put("run", export.run());
        digestSource.put("nodeStatusCounts", export.nodeStatusCounts());
        digestSource.put("redactionPolicy", export.redactionPolicy());
        try {
            return SensitiveTextSanitizer.sha256Hex(objectMapper.writeValueAsString(digestSource));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_SOURCE_RUN_EXPORT_INVALID");
        }
    }

    private NormalizedGenerateRequest normalize(GenerateReportCommand command) {
        if (command == null
                || !StringUtils.hasText(command.projectId())
                || command.executionRunId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_GENERATE_REQUEST_INVALID");
        }
        PlatformContext context = contextClient.projectContext(command.projectId());
        return new NormalizedGenerateRequest(
                SensitiveTextSanitizer.boundedText(context.resourceId(), 64),
                command.executionRunId(),
                SensitiveTextSanitizer.boundedNullableText(command.requestKey(), 128),
                SensitiveTextSanitizer.boundedNullableText(command.reason(), 256)
        );
    }

    private ReportQuery normalizeQuery(ReportQuery query) {
        String projectId = query.projectId();
        if (StringUtils.hasText(projectId)) {
            projectId = contextClient.projectContext(projectId).resourceId();
        }
        return new ReportQuery(projectId, query.executionRunId(), query.status(), query.index(), query.size());
    }

    private boolean sameProject(String normalizedProjectId, String sourceProjectId) {
        if (!StringUtils.hasText(sourceProjectId)) {
            return false;
        }
        if (normalizedProjectId.equals(sourceProjectId)) {
            return true;
        }
        return normalizedProjectId.equals(contextClient.projectContext(sourceProjectId).resourceId());
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DISABLED");
        }
        if (!properties.generateEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_GENERATE_DISABLED");
        }
    }

    private void requireDiagnosisEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DISABLED");
        }
        if (!properties.diagnosisEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DIAGNOSIS_DISABLED");
        }
    }

    private ReportExecutionReport requireReport(UUID id) {
        return repository.report(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_NOT_FOUND"));
    }

    private void audit(ReportExecutionReport report, String action, String result, Map<String, Object> afterJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.id());
        payload.put("projectId", report.projectId());
        payload.put("executionRunId", report.executionRunId());
        payload.putAll(afterJson);
        contextClient.writeAuditEvent(
                action,
                "REPORT_EXECUTION_REPORT",
                report.id().toString(),
                report.projectId(),
                result,
                payload
        );
    }

    private record NormalizedGenerateRequest(
            String projectId,
            UUID executionRunId,
            String requestKey,
            String reason
    ) {
    }

    private record ReportBundle(
            ReportExecutionReport report,
            List<ReportEvidenceManifest> evidenceManifests,
            ReportFailureDiagnosis failureDiagnosis
    ) {
    }

    private record Wp8EvidenceRefs(
            List<UUID> dataSetRefs,
            List<UUID> accountLeaseRefs,
            List<UUID> cleanupTaskRefs,
            boolean truncated
    ) {

        private boolean empty() {
            return dataSetRefs.isEmpty() && accountLeaseRefs.isEmpty() && cleanupTaskRefs.isEmpty();
        }

        private int size() {
            return dataSetRefs.size() + accountLeaseRefs.size() + cleanupTaskRefs.size();
        }
    }
}
