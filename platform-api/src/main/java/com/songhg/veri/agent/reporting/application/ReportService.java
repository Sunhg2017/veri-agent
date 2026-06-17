package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.asset.application.AssetCrossWpReportEvidenceService;
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
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpReportEvidenceService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private final ReportingRepository repository;
    private final ExecutionRunService executionRunService;
    private final ReportingProperties properties;
    private final ReportingActorResolver actorResolver;
    private final ReportingPlatformContextClient contextClient;
    private final ObjectMapper objectMapper;
    private final ReportingJsonSupport jsonSupport;
    private final ReportResponseMapper responseMapper;
    private final RuleFailureClassifier failureClassifier;
    private final ReportDiagnosisContextBuilder diagnosisContextBuilder;
    private final ReportDiagnosisAiInvoker diagnosisAiInvoker;
    private final ReportEvidenceAssembler evidenceAssembler;

    public ReportService(
            ReportingRepository repository,
            ExecutionRunService executionRunService,
            ObjectProvider<TestDataCrossWpReferenceService> testDataServices,
            ObjectProvider<AssetCrossWpReportEvidenceService> assetEvidenceServices,
            ObjectProvider<TestDesignCrossWpReportEvidenceService> testDesignEvidenceServices,
            ObjectProvider<ModelInvocationService> modelInvocationServices,
            ReportingProperties properties,
            ReportingActorResolver actorResolver,
            ReportingPlatformContextClient contextClient,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.executionRunService = executionRunService;
        this.properties = properties;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
        this.jsonSupport = new ReportingJsonSupport(objectMapper);
        this.responseMapper = new ReportResponseMapper(jsonSupport);
        this.failureClassifier = new RuleFailureClassifier(jsonSupport);
        this.diagnosisContextBuilder = new ReportDiagnosisContextBuilder(properties, jsonSupport);
        this.diagnosisAiInvoker = new ReportDiagnosisAiInvoker(modelInvocationServices, actorResolver);
        this.evidenceAssembler = new ReportEvidenceAssembler(
                testDataServices,
                assetEvidenceServices,
                testDesignEvidenceServices,
                properties,
                jsonSupport
        );
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
                            repository.defectDrafts(report.id()),
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
                repository.defectDrafts(report.id()),
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
                repository.defectDrafts(regenerated.id()),
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
                    repository.defectDrafts(current.id()),
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
                repository.defectDrafts(archived.id()),
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
                            repository.defectDrafts(existing.id()),
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
                List.of(),
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
        ReportEvidenceAssembler.EvidenceRefs evidenceRefs = evidenceAssembler.evidenceRefs(run.nodes());
        List<ReportEvidenceManifest> evidenceManifests =
                evidenceAssembler.evidenceManifests(reportId, request.projectId(), export, evidenceRefs, now);
        ReportFailureDiagnosis failureDiagnosis = failureClassifier.classify(reportId, evidenceManifests, now);
        Map<String, Object> summary = reportSummary(export, request, evidenceManifests, evidenceRefs,
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
            ReportEvidenceAssembler.EvidenceRefs evidenceRefs,
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
        summary.put("evidenceManifestTruncated", estimatedEvidenceCount(run, evidenceRefs) > evidenceManifests.size());
        summary.put("wp8EvidenceReferenceCount", evidenceRefs.wp8Refs().size());
        summary.put("wp8EvidenceManifestCount", evidenceManifests.stream()
                .filter(manifest -> "WP8".equals(manifest.sourceWp()))
                .count());
        summary.put("wp8EvidenceReferenceTruncated", evidenceRefs.wp8Refs().truncated());
        summary.put("wp3EvidenceReferenceCount", evidenceRefs.wp3Refs().size());
        summary.put("wp3EvidenceManifestCount", evidenceManifests.stream()
                .filter(manifest -> "WP3".equals(manifest.sourceWp()))
                .count());
        summary.put("wp3EvidenceReferenceTruncated", evidenceRefs.wp3Refs().truncated());
        summary.put("wp5EvidenceReferenceCount", evidenceRefs.wp5Refs().size());
        summary.put("wp5EvidenceManifestCount", evidenceManifests.stream()
                .filter(manifest -> "WP5".equals(manifest.sourceWp()))
                .count());
        summary.put("wp5EvidenceReferenceTruncated", evidenceRefs.wp5Refs().truncated());
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

    private int estimatedEvidenceCount(
            ExecutionRunDetailResponse run,
            ReportEvidenceAssembler.EvidenceRefs evidenceRefs
    ) {
        return run.nodes().size() + evidenceRefs.size();
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

}
