package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataRunnerAccountContractResponse;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.command.CancelUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunPageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunQuery;
import com.songhg.veri.agent.uie2e.application.view.UiE2eArtifactManifestResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eFlakyMarkResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunExportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunStepResultResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunSummaryResponse;
import com.songhg.veri.agent.uie2e.domain.UiE2eArtifactManifest;
import com.songhg.veri.agent.uie2e.domain.UiE2eFlakyMark;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import com.songhg.veri.agent.uie2e.domain.UiE2eRunStepResult;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UiE2eRunService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> RUN_STATUSES = Set.of(
            "QUEUED",
            "RUNNING",
            "SUCCEEDED",
            "FAILED",
            "TIMEOUT",
            "CANCELED",
            "BLOCKED"
    );
    private static final Set<String> TERMINAL_RUN_STATUSES = Set.of("SUCCEEDED", "FAILED", "TIMEOUT", "CANCELED", "BLOCKED");
    private static final Set<String> FAILURE_BUCKETS = Set.of(
            "LOCATOR",
            "AUTHORIZATION",
            "ENVIRONMENT_TIMEOUT",
            "ACCOUNT",
            "TEST_DATA",
            "RUNNER",
            "ASSERTION",
            "UNKNOWN"
    );
    private static final Set<String> STEP_RESULT_STATUSES = Set.of(
            "PENDING",
            "RUNNING",
            "SUCCEEDED",
            "FAILED",
            "SKIPPED",
            "BLOCKED",
            "TIMEOUT",
            "CANCELED"
    );
    private static final Set<String> ARTIFACT_TYPES = Set.of("SCREENSHOT", "VIDEO", "TRACE", "LOG", "HAR", "JUNIT_XML");
    private static final Set<String> ARTIFACT_CAPTURE_STATUSES = Set.of("PENDING", "CAPTURED", "REDACTED", "BLOCKED", "FAILED", "SKIPPED");
    private static final Set<String> VISUAL_FAILURE_CODES = Set.of("UI_E2E_VISUAL_REGRESSION_FAILED");
    private static final Pattern UNSAFE_RUNNER_SUMMARY_KEY_PATTERN = Pattern.compile(
            "(?i).*(authorization|cookie|password|passwd|secretref|secreturi|secretvalue|secretplaintext|token|payload|stdout|stderr"
                    + "|request|response|body|dom|html|sourceCode|sourceUrl|webhook).*"
    );

    private final UiE2eRepository repository;
    private final UiE2eActorResolver actorResolver;
    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eProperties properties;
    private final UiE2eRunnerPort runnerPort;
    private final UiE2eRunEnvironmentResolver environmentResolver;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;
    private final UiE2eArtifactStorage artifactStorage;
    private final ObjectMapper objectMapper;
    private final AsyncTaskNotificationService notificationService;
    private final UiE2eRunnerExecutionPool executionPool;
    private final UiE2eRunAttemptAggregator attemptAggregator;
    private final UiE2eRunAttemptExecutor attemptExecutor;

    public UiE2eRunService(
            UiE2eRepository repository,
            UiE2eActorResolver actorResolver,
            UiE2ePlatformContextClient contextClient,
            UiE2eProperties properties,
            UiE2eRunnerPort runnerPort,
            UiE2eRunEnvironmentResolver environmentResolver,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            UiE2eArtifactStorage artifactStorage,
            ObjectMapper objectMapper,
            AsyncTaskNotificationService notificationService,
            UiE2eRunnerExecutionPool executionPool
    ) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.properties = properties;
        this.runnerPort = runnerPort;
        this.environmentResolver = environmentResolver;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
        this.artifactStorage = artifactStorage;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.executionPool = executionPool;
        this.attemptAggregator = new UiE2eRunAttemptAggregator(repository, artifactStorage, properties, objectMapper);
        this.attemptExecutor = new UiE2eRunAttemptExecutor(runnerPort, executionPool);
    }

    public UiE2eRunnerExecutionPool.CapacitySnapshot runnerCapacity() {
        return executionPool.snapshot();
    }

    /**
     * Creates one aggregate-only UI/E2E run snapshot. Repeated requestKey returns the existing run to keep operator
     * retries idempotent before asynchronous runner execution exists.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eRunDetailResponse createRun(CreateUiE2eRunCommand command) {
        assertEnabled();
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "run 请求不能为空");
        }
        UiE2eScene scene = requireScene(command.sceneId());
        UiE2eBundle bundle = requireBundle(command.bundleId());
        assertSameProject(command.projectId(), scene.projectId(), bundle.projectId());
        assertRunnableScene(scene);
        assertRunnableBundle(bundle, scene);
        UiE2eRunExecutionOptions executionOptions = UiE2eRunExecutionOptions.from(command);
        String requestKey = boundedRequestKey(command.requestKey());
        if (StringUtils.hasText(requestKey)) {
            return repository.runByProjectSceneAndRequestKey(scene.projectId(), scene.id(), compositeRequestKey(requestKey, executionOptions))
                    .map(existing -> detail(existing, true))
                    .orElseGet(() -> createRun(scene, bundle, command, requestKey, executionOptions));
        }
        return createRun(scene, bundle, command, null, executionOptions);
    }

    @Transactional(readOnly = true)
    public PageResponse<UiE2eRunSummaryResponse> runs(UiE2eRunPageRequest request) {
        assertEnabled();
        UiE2eRunPageRequest effectiveRequest = request == null ? new UiE2eRunPageRequest() : request;
        UiE2eRunQuery query = normalizeQuery(effectiveRequest.toQuery());
        List<UiE2eRunSummaryResponse> items = repository.runs(query).stream()
                .map(this::summary)
                .toList();
        return PageResponse.of(items, effectiveRequest.getIndex(), effectiveRequest.getSize(), repository.countRuns(query));
    }

    @Transactional(readOnly = true)
    public UiE2eRunDetailResponse run(UUID id) {
        assertEnabled();
        return detail(requireRun(id), false);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eRunDetailResponse cancelRun(UUID id, CancelUiE2eRunCommand command) {
        assertEnabled();
        UiE2eRun run = requireRun(id);
        if (TERMINAL_RUN_STATUSES.contains(run.status())) {
            return detail(run, false);
        }
        UiE2eRunnerPort.RunnerCancelResult cancelResult = runnerPort.cancel(id);
        Instant now = Instant.now();
        Map<String, Object> accountSummary = readMap(run.accountSummaryJson());
        UiE2eRun canceled = new UiE2eRun(
                run.id(),
                run.sceneId(),
                run.bundleId(),
                run.projectId(),
                "CANCELED",
                run.requestKey(),
                run.runnerMode(),
                run.baseUrlDigest(),
                run.accountLeaseRef(),
                json(accountSummary),
                run.executionSummaryJson(),
                cancelResult != null && StringUtils.hasText(cancelResult.errorCode())
                        ? SensitiveTextSanitizer.boundedNullableText(cancelResult.errorCode(), 64)
                        : "UI_E2E_RUNNER_CANCELED",
                cancelResult != null
                        ? SensitiveTextSanitizer.sanitizedEvidenceText(cancelResult.errorSummary(), 512)
                        : "UI/E2E run canceled",
                run.traceId(),
                run.createdBy(),
                run.startedAt(),
                now,
                run.createdAt(),
                now
        );
        repository.updateRun(canceled);
        auditRun(canceled, "SUCCESS", "ui_e2e.run.canceled", Map.of(
                "previousStatus", run.status(),
                "status", canceled.status(),
                "runnerCancelAccepted", cancelResult != null && cancelResult.accepted()
        ));
        notificationService.notifyUiE2eRunFinished(canceled);
        return detail(canceled, false);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eRunExportResponse exportRun(UUID id) {
        assertEnabled();
        if (!properties.exportEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_EXPORT_DISABLED");
        }
        UiE2eRun run = requireRun(id);
        UiE2eRunDetailResponse detail = detail(run, false);
        auditRun(run, "SUCCESS", "ui_e2e.run.exported", Map.of(
                "status", run.status(),
                "rawBaseUrlExported", false,
                "secretRefPlaintextExported", false,
                "artifactDownloadExported", false
        ));
        return new UiE2eRunExportResponse(
                "wp7-run-export-v1",
                Instant.now(),
                detail,
                Map.of(
                        "aggregateOnly", true,
                        "rawBaseUrlExported", false,
                        "secretRefPlaintextExported", false,
                        "runnerOutputExported", false,
                        "artifactDownloadReady", false
                )
        );
    }

    @Transactional(readOnly = true)
    public String runProjectScopeId(UUID id) {
        return repository.runProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E run 不存在"));
    }

    /**
     * Reads one raw artifact from the controlled storage root when the run, manifest and download policy all allow it.
     */
    @Transactional(readOnly = true)
    public DownloadableArtifact downloadArtifact(UUID runId, UUID artifactId) {
        assertEnabled();
        UiE2eRun run = requireRun(runId);
        UiE2eArtifactManifest manifest = repository.artifacts(run.id()).stream()
                .filter(item -> artifactId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E artifact 不存在"));
        if (artifactStorage == null || !artifactStorage.isDownloadReady(manifest.storageRef())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY");
        }
        try {
            UiE2eArtifactStorage.StoredArtifactContent content = artifactStorage.read(manifest.storageRef());
            return new DownloadableArtifact(
                    content.fileName(),
                    normalizeContentType(content.contentType()),
                    content.content()
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY");
        }
    }

    private UiE2eRunDetailResponse createRun(
            UiE2eScene scene,
            UiE2eBundle bundle,
            CreateUiE2eRunCommand command,
            String requestKey,
            UiE2eRunExecutionOptions executionOptions
    ) {
        UUID runId = UUID.randomUUID();
        UiE2eRunEnvironmentResolver.ResolvedUiE2eBaseUrl resolvedBaseUrl =
                environmentResolver.resolve(scene.projectId(), command.baseUrlRef());
        assertAllowlist(resolvedBaseUrl.normalizedHost());
        TestDataRunnerAccountContractResponse runnerAccountContract = runnerAccount(command.accountLeaseRef(), scene.projectId());
        Map<String, Object> accountSummary = accountSummary(runnerAccountContract);
        UiE2eRunnerPort.RunnerValidation validation = runnerPort.validate(new UiE2eRunnerPort.RunnerValidationRequest(
                scene.id(),
                bundle.id(),
                scene.projectId(),
                resolvedBaseUrl.normalizedBaseUrl(),
                runnerAccountContract.accountLeaseRef().toString(),
                accountSummary,
                executionOptions.browserTypes(),
                executionOptions.visualRegressionEnabled(),
                executionOptions.baselineRunId(),
                executionOptions.visualMismatchThreshold()
        ));
        if (validation != null && !validation.accepted()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, firstText(validation.errorCode(), "EXECUTION_RUNNER_NOT_READY"));
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        String traceId = TraceContext.getOrCreateTraceId();
        UUID baselineRunId = resolvedBaselineRunId(scene, executionOptions);
        List<UiE2eRunAttemptAggregator.BrowserAttempt> browserAttempts = attemptExecutor.execute(
                runId,
                scene.id(),
                bundle.id(),
                scene.projectId(),
                resolvedBaseUrl.normalizedBaseUrl(),
                runnerAccountContract.accountLeaseRef().toString(),
                accountSummary,
                executionOptions,
                baselineRunId
        );
        UiE2eRunnerPort.RunnerRunResult attempt = attemptAggregator.aggregate(runId, baselineRunId, executionOptions, browserAttempts);
        Map<String, Object> executionSummary = buildExecutionSummary(
                attempt,
                executionOptions,
                baselineRunId,
                runnerAccountContract,
                resolvedBaseUrl
        );
        UiE2eRun run = runFromAttempt(
                attempt,
                runId,
                scene,
                bundle,
                compositeRequestKey(requestKey, executionOptions),
                runnerAccountContract,
                resolvedBaseUrl,
                executionSummary,
                actor,
                traceId,
                now
        );
        List<UiE2eArtifactManifest> manifests = manifests(run, attempt, actor, now);
        List<UiE2eRunStepResult> stepResults = stepResults(run, scene, attempt, actor, now);
        try {
            repository.insertRun(run);
        } catch (DuplicateKeyException exception) {
            if (StringUtils.hasText(requestKey)) {
                return repository.runByProjectSceneAndRequestKey(scene.projectId(), scene.id(), compositeRequestKey(requestKey, executionOptions))
                        .map(existing -> detail(existing, true))
                        .orElseThrow(() -> exception);
            }
            throw exception;
        }
        repository.replaceRunStepResults(run.id(), stepResults);
        repository.replaceArtifacts(run.id(), manifests);
        Map<String, Object> createdAudit = new LinkedHashMap<>();
        createdAudit.put("status", run.status());
        createdAudit.put("runnerMode", run.runnerMode());
        createdAudit.put("baseUrlRefDigest", resolvedBaseUrl.summary().get("baseUrlRefDigest"));
        createdAudit.put("accountLeaseRefPresent", true);
        createdAudit.put("browserTypes", executionOptions.browserTypes());
        createdAudit.put("visualRegressionEnabled", executionOptions.visualRegressionEnabled());
        auditRun(run, "SUCCESS", "ui_e2e.run.created", createdAudit);

        Map<String, Object> startedAudit = new LinkedHashMap<>();
        startedAudit.put("status", run.status());
        startedAudit.put("runnerMode", run.runnerMode());
        startedAudit.put("failureCode", run.failureCode());
        auditRun(run, "SUCCESS", "ui_e2e.run.started", startedAudit);
        if (TERMINAL_RUN_STATUSES.contains(run.status())) {
            Map<String, Object> completedAudit = new LinkedHashMap<>();
            completedAudit.put("status", run.status());
            completedAudit.put("failureCode", run.failureCode());
            completedAudit.put("stepResultCount", stepResults.size());
            completedAudit.put("artifactManifestCount", manifests.size());
            auditRun(run, "BLOCKED".equals(run.status()) ? "FAILED" : "SUCCESS", "ui_e2e.run.completed", completedAudit);
            notificationService.notifyUiE2eRunFinished(run);
        }
        return detail(run, false);
    }

    private UiE2eRun runFromAttempt(
            UiE2eRunnerPort.RunnerRunResult attempt,
            UUID runId,
            UiE2eScene scene,
            UiE2eBundle bundle,
        String requestKey,
        TestDataRunnerAccountContractResponse runnerAccountContract,
        UiE2eRunEnvironmentResolver.ResolvedUiE2eBaseUrl resolvedBaseUrl,
        Map<String, Object> executionSummary,
        String actor,
        String traceId,
        Instant now
    ) {
        // WP7 only exposes the run control-plane. Even when execution is disabled, we persist a terminal run snapshot so
        // operators can audit the request and retry idempotently once an asynchronous runner is introduced.
        String status = normalizeRunStatus(attempt == null ? null : attempt.status());
        String runnerMode = normalizeRunnerMode(attempt == null ? null : attempt.runnerMode());
        Instant finishedAt = TERMINAL_RUN_STATUSES.contains(status) ? now : null;
        return new UiE2eRun(
                runId,
                scene.id(),
                bundle.id(),
                scene.projectId(),
                status,
                requestKey,
                runnerMode,
                SensitiveTextSanitizer.sha256Hex(
                        resolvedBaseUrl.baseUrlRef() + ":" + resolvedBaseUrl.normalizedHost() + ":" + resolvedBaseUrl.environmentKey()
                ),
                runnerAccountContract.accountLeaseRef().toString(),
                json(accountSummary(runnerAccountContract)),
                json(executionSummary),
                SensitiveTextSanitizer.boundedNullableText(attempt == null ? null : attempt.failureCode(), 64),
                SensitiveTextSanitizer.sanitizedEvidenceText(attempt == null ? null : attempt.failureSummary(), 512),
                traceId,
                actor,
                now,
                finishedAt,
                now,
                finishedAt == null ? now : finishedAt
        );
    }

    private void assertAllowlist(String normalizedHost) {
        if (properties.allowlistBaseUrls() == null || properties.allowlistBaseUrls().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
        boolean allowed = properties.allowlistBaseUrls().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizedAllowlistHost)
                .anyMatch(normalizedHost::equals);
        if (!allowed) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_BASE_URL_NOT_ALLOWED");
        }
    }

    private String normalizedAllowlistHost(String url) {
        try {
            java.net.URI uri = new java.net.URI(url.trim());
            return uri.getHost() == null ? "" : java.net.IDN.toASCII(uri.getHost().trim().toLowerCase(Locale.ROOT));
        } catch (Exception exception) {
            return "";
        }
    }

    private TestDataRunnerAccountContractResponse runnerAccount(UUID accountLeaseRef, String projectId) {
        try {
            TestDataRunnerAccountContractResponse contract = testDataCrossWpReferenceService.runnerAccountContract(accountLeaseRef);
            if (contract.account() == null || !projectId.equals(contract.account().projectId())) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID");
            }
            return contract;
        } catch (BusinessException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_ACCOUNT_LEASE_INVALID");
        }
    }

    private Map<String, Object> accountSummary(TestDataRunnerAccountContractResponse contract) {
        TestDataCrossWpAccountSummary account = contract.account();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("accountLeaseRef", contract.accountLeaseRef().toString());
        summary.put("status", contract.status());
        summary.put("expiresAt", contract.expiresAt() == null ? null : contract.expiresAt().toString());
        if (account != null) {
            summary.put("accountKey", account.accountKey());
            summary.put("displayName", account.displayName());
            summary.put("accountStatus", account.status());
            summary.put("roleTags", account.roleTags());
            summary.put("scopeSummaryKeys", account.scopeSummary() == null
                    ? List.of()
                    : account.scopeSummary().keySet().stream().map(String::valueOf).sorted().toList());
            summary.put("secretRefDigest", account.secretRefDigest());
            summary.put("lastHealthStatus", account.lastHealthStatus());
        }
        summary.put("secretPlaintextReturned", false);
        summary.put("aggregateOnly", true);
        return summary;
    }

    private UiE2eRunSummaryResponse summary(UiE2eRun run) {
        UiE2eScene scene = requireScene(run.sceneId());
        return new UiE2eRunSummaryResponse(
                run.id(),
                run.projectId(),
                run.sceneId(),
                scene.code(),
                scene.name(),
                run.bundleId(),
                run.status(),
                displayRequestKey(run.requestKey()),
                run.runnerMode(),
                run.failureCode(),
                run.failureSummary(),
                run.traceId(),
                readMap(run.accountSummaryJson()),
                repository.flakyMarkByRun(run.id())
                        .or(() -> repository.flakyMarkByScene(run.sceneId()))
                        .map(UiE2eFlakyMark::status)
                        .orElse(null),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    private UiE2eRunDetailResponse detail(UiE2eRun run, boolean idempotentReplay) {
        UiE2eScene scene = requireScene(run.sceneId());
        UiE2eBundle bundle = requireBundle(run.bundleId());
        Map<String, Object> accountSummary = readMap(run.accountSummaryJson());
        List<UiE2eRunStepResultResponse> stepResults = repository.runStepResults(run.id()).stream()
                .map(this::stepResultResponse)
                .toList();
        List<UiE2eArtifactManifestResponse> artifacts = repository.artifacts(run.id()).stream()
                .map(this::artifactResponse)
                .toList();
        UiE2eFlakyMarkResponse flakyMark = repository.flakyMarkByRun(run.id())
                .map(mark -> flakyResponse(mark, scene, run))
                .orElseGet(() -> repository.flakyMarkByScene(scene.id())
                        .map(mark -> flakyResponse(mark, scene, run))
                        .orElse(null));
        Map<String, Object> executionSummary = new LinkedHashMap<>(readMap(run.executionSummaryJson()));
        // runnerReady reflects that the control-plane contracts are live. runnerDefaultDisabled remains the switch that
        // tells the caller whether this environment will actually execute the run by default.
        enrichExecutionSummary(executionSummary, run, accountSummary, stepResults, artifacts, flakyMark);
        return new UiE2eRunDetailResponse(
                run.id(),
                run.projectId(),
                run.sceneId(),
                scene.code(),
                scene.name(),
                scene.status(),
                run.bundleId(),
                bundle.status(),
                run.status(),
                displayRequestKey(run.requestKey()),
                run.runnerMode(),
                run.failureCode(),
                run.failureSummary(),
                run.traceId(),
                accountSummary,
                executionSummary,
                stepResults,
                artifacts,
                flakyMark,
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt(),
                idempotentReplay
        );
    }

    /**
     * Stores only step-level aggregate summaries so WP9/WP10 can consume classification and timing without DOM,
     * scripts or credential-bearing runner output.
     */
    private List<UiE2eRunStepResult> stepResults(
            UiE2eRun run,
            UiE2eScene scene,
            UiE2eRunnerPort.RunnerRunResult attempt,
            String actor,
            Instant now
    ) {
        if (attempt == null || attempt.stepResults() == null || attempt.stepResults().isEmpty()) {
            return List.of();
        }
        Map<Integer, UiE2eSceneStep> sceneStepsByOrder = repository.sceneSteps(scene.id()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        UiE2eSceneStep::stepOrder,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return attempt.stepResults().stream()
                .filter(Objects::nonNull)
                .map(step -> stepResult(run, sceneStepsByOrder, step, actor, now))
                .sorted(Comparator.comparingInt(UiE2eRunStepResult::stepOrder).thenComparing(UiE2eRunStepResult::id))
                .toList();
    }

    private UiE2eRunStepResult stepResult(
            UiE2eRun run,
            Map<Integer, UiE2eSceneStep> sceneStepsByOrder,
            UiE2eRunnerPort.RunnerStepResult step,
            String actor,
            Instant now
    ) {
        int stepOrder = Math.max(step.stepOrder(), 1);
        UiE2eSceneStep sceneStep = sceneStepsByOrder.get(stepOrder);
        UUID sceneStepId = sceneStep == null ? step.sceneStepId() : sceneStep.id();
        SanitizedMap safeRunnerSummary = safeRunnerSummary(step.summary());
        Map<String, Object> safeSummary = new LinkedHashMap<>(safeRunnerSummary.value());
        safeSummary.put("aggregateOnly", true);
        safeSummary.put("rawDomStored", false);
        safeSummary.put("rawRunnerOutputStored", false);
        safeSummary.put("secretPlaintextStored", false);
        safeSummary.put("unsafeSummaryKeysFiltered", safeRunnerSummary.filteredUnsafeKeys());
        return new UiE2eRunStepResult(
                UUID.randomUUID(),
                run.id(),
                sceneStepId,
                stepOrder,
                normalizeStepResultStatus(step.status()),
                Math.max(step.durationMs(), 0),
                normalizeFailureBucket(step.failureBucket()),
                SensitiveTextSanitizer.boundedNullableText(step.errorCode(), 64),
                json(safeSummary),
                actor,
                actor,
                now,
                now
        );
    }

    private void auditRun(UiE2eRun run, String result, String action, Map<String, Object> afterJson) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("runId", run.id().toString());
        payload.put("sceneId", run.sceneId().toString());
        payload.put("bundleId", run.bundleId().toString());
        payload.put("status", run.status());
        payload.put("traceId", run.traceId());
        contextClient.writeAuditEvent(action, "UI_E2E_RUN", run.id().toString(), run.projectId(), result, payload);
    }

    private List<UiE2eArtifactManifest> manifests(
            UiE2eRun run,
            UiE2eRunnerPort.RunnerRunResult attempt,
            String actor,
            Instant now
    ) {
        if (attempt == null || attempt.artifacts() == null || attempt.artifacts().isEmpty()) {
            return List.of();
        }
        return attempt.artifacts().stream()
                .filter(Objects::nonNull)
                .limit(properties.effectiveMaxArtifactCount())
                .map(artifact -> artifact(run, artifact, actor, now))
                .sorted(Comparator.comparing(UiE2eArtifactManifest::createdAt).thenComparing(UiE2eArtifactManifest::id))
                .toList();
    }

    private UiE2eArtifactManifest artifact(
            UiE2eRun run,
            UiE2eRunnerPort.RunnerArtifactManifest artifact,
            String actor,
            Instant now
    ) {
        String artifactType = normalizeArtifactType(artifact.artifactType());
        String captureStatus = normalizeArtifactCaptureStatus(artifact.captureStatus());
        String storageRef = boundedStorageRef(artifact.storageRef());
        String digest = boundedDigest(artifact.artifactDigest());
        long sizeBytes = Math.max(0L, Math.min(artifact.sizeBytes(), properties.effectiveMaxArtifactSizeBytes()));
        SanitizedMap safeArtifactFlags = safeRunnerSummary(artifact.redactionFlags());
        Map<String, Object> flags = new LinkedHashMap<>(safeArtifactFlags.value());
        flags.put("aggregateOnly", true);
        boolean downloadReady = artifactStorage.isDownloadReady(storageRef);
        flags.put("rawArtifactStored", downloadReady);
        flags.put("rawArtifactDownloadReady", downloadReady);
        flags.put("secretPlaintextStored", false);
        flags.put("storageCredentialStored", false);
        flags.put("unsafeRedactionFlagKeysFiltered", safeArtifactFlags.filteredUnsafeKeys());
        if ("CAPTURED".equals(captureStatus) && (!StringUtils.hasText(storageRef) || !StringUtils.hasText(digest))) {
            captureStatus = "BLOCKED";
            flags.put("captureBlockedReason", "artifactRefIncomplete");
        }
        return new UiE2eArtifactManifest(
                UUID.randomUUID(),
                run.id(),
                artifactType,
                storageRef,
                digest,
                sizeBytes,
                json(flags),
                captureStatus,
                actor,
                actor,
                now,
                now
        );
    }

    private Object safeManifestValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return safeRunnerSummary(map).value();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::safeManifestValue).toList();
        }
        if (value instanceof String text) {
            return SensitiveTextSanitizer.sanitizedEvidenceText(text, 256);
        }
        return value;
    }

    /**
     * Drops runner-provided dangerous summary keys so aggregate-only responses never echo stdout/stderr, payload-like
     * fields or credential-bearing key names even when the values themselves have already been text-redacted.
     */
    private SanitizedMap safeRunnerSummary(Map<?, ?> summary) {
        if (summary == null || summary.isEmpty()) {
            return new SanitizedMap(Map.of(), false);
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        boolean filteredUnsafeKeys = false;
        for (Map.Entry<?, ?> entry : summary.entrySet()) {
            String normalizedKey = SensitiveTextSanitizer.boundedText(String.valueOf(entry.getKey()), 64);
            if (!safeRunnerSummaryKey(normalizedKey)) {
                filteredUnsafeKeys = true;
                continue;
            }
            Object safeValue = safeManifestValue(entry.getValue());
            safe.put(normalizedKey, safeValue);
        }
        return new SanitizedMap(Map.copyOf(safe), filteredUnsafeKeys);
    }

    private boolean safeRunnerSummaryKey(String key) {
        return StringUtils.hasText(key) && !UNSAFE_RUNNER_SUMMARY_KEY_PATTERN.matcher(key).matches();
    }

    private UiE2eArtifactManifestResponse artifactResponse(UiE2eArtifactManifest manifest) {
        Map<String, Object> redactionFlags = new LinkedHashMap<>(readMap(manifest.redactionFlagsJson()));
        boolean downloadReady = downloadReady(manifest);
        redactionFlags.put("rawArtifactStored", downloadReady);
        redactionFlags.put("rawArtifactDownloadReady", downloadReady);
        return new UiE2eArtifactManifestResponse(
                manifest.id(),
                manifest.artifactType(),
                manifest.storageRef(),
                manifest.artifactDigest(),
                manifest.sizeBytes(),
                redactionFlags,
                manifest.captureStatus(),
                manifest.createdAt(),
                manifest.updatedAt()
        );
    }

    private UiE2eFlakyMarkResponse flakyResponse(UiE2eFlakyMark mark, UiE2eScene scene, UiE2eRun run) {
        return new UiE2eFlakyMarkResponse(
                mark.id(),
                mark.projectId(),
                mark.sceneId(),
                scene.code(),
                scene.name(),
                scene.riskLevel(),
                mark.runId(),
                1,
                run.status(),
                null,
                mark.status(),
                mark.reasonCode(),
                mark.reasonSummary(),
                mark.createdBy(),
                mark.updatedBy(),
                mark.createdAt(),
                mark.updatedAt()
        );
    }

    private UiE2eRunStepResultResponse stepResultResponse(UiE2eRunStepResult stepResult) {
        return new UiE2eRunStepResultResponse(
                stepResult.id(),
                stepResult.sceneStepId(),
                stepResult.stepOrder(),
                stepResult.status(),
                stepResult.durationMs(),
                stepResult.failureBucket(),
                stepResult.errorCode(),
                readMap(stepResult.summaryJson()),
                stepResult.createdAt(),
                stepResult.updatedAt()
        );
    }

    private Map<String, Integer> stepStatusCounts(List<UiE2eRunStepResultResponse> stepResults) {
        return countsByKey(stepResults, UiE2eRunStepResultResponse::status, STEP_RESULT_STATUSES);
    }

    private Map<String, Integer> failureBucketCounts(List<UiE2eRunStepResultResponse> stepResults) {
        return countsByKey(stepResults, UiE2eRunStepResultResponse::failureBucket, FAILURE_BUCKETS);
    }

    private <T> Map<String, Integer> countsByKey(
            List<T> items,
            Function<T, String> extractor,
            Set<String> allowedValues
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        allowedValues.stream().sorted().forEach(value -> counts.put(value, 0));
        for (T item : items) {
            if (item == null) {
                continue;
            }
            String value = extractor.apply(item);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (!allowedValues.contains(normalized)) {
                continue;
            }
            counts.computeIfPresent(normalized, (ignored, current) -> current + 1);
        }
        return counts;
    }

    private UiE2eScene requireScene(UUID id) {
        return repository.scene(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E 场景不存在"));
    }

    private UiE2eBundle requireBundle(UUID id) {
        return repository.bundle(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E bundle 不存在"));
    }

    private UiE2eRun requireRun(UUID id) {
        return repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E run 不存在"));
    }

    private void assertSameProject(String requestedProjectId, String sceneProjectId, String bundleProjectId) {
        if (!sceneProjectId.equals(requestedProjectId) || !bundleProjectId.equals(requestedProjectId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        contextClient.projectContext(requestedProjectId);
    }

    private void assertRunnableScene(UiE2eScene scene) {
        if (!"APPROVED".equals(scene.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_SCENE_NOT_READY");
        }
    }

    private void assertRunnableBundle(UiE2eBundle bundle, UiE2eScene scene) {
        if (!scene.id().equals(bundle.sceneId()) || !"APPROVED".equals(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_BUNDLE_NOT_READY");
        }
    }

    private String normalizeRunStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "BLOCKED";
        return RUN_STATUSES.contains(normalized) ? normalized : "BLOCKED";
    }

    private String normalizeRunnerMode(String runnerMode) {
        String normalized = StringUtils.hasText(runnerMode) ? runnerMode.trim().toUpperCase(Locale.ROOT) : "DISABLED";
        return Set.of("DISABLED", "MANAGED", "HTTP_ADAPTER", "PLAYWRIGHT_SUBPROCESS").contains(normalized)
                ? normalized
                : "DISABLED";
    }

    private String normalizeArtifactType(String artifactType) {
        String normalized = StringUtils.hasText(artifactType) ? artifactType.trim().toUpperCase(Locale.ROOT) : "LOG";
        return ARTIFACT_TYPES.contains(normalized) ? normalized : "LOG";
    }

    private String normalizeStepResultStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "BLOCKED";
        return STEP_RESULT_STATUSES.contains(normalized) ? normalized : "BLOCKED";
    }

    private String normalizeFailureBucket(String failureBucket) {
        if (!StringUtils.hasText(failureBucket)) {
            return null;
        }
        String normalized = failureBucket.trim().toUpperCase(Locale.ROOT);
        return FAILURE_BUCKETS.contains(normalized) ? normalized : "UNKNOWN";
    }

    private String normalizeArtifactCaptureStatus(String captureStatus) {
        String normalized = StringUtils.hasText(captureStatus) ? captureStatus.trim().toUpperCase(Locale.ROOT) : "PENDING";
        return ARTIFACT_CAPTURE_STATUSES.contains(normalized) ? normalized : "BLOCKED";
    }

    private String boundedStorageRef(String storageRef) {
        return SensitiveTextSanitizer.sanitizedEvidenceText(storageRef, 512);
    }

    private String boundedDigest(String digest) {
        if (!StringUtils.hasText(digest)) {
            return null;
        }
        String bounded = SensitiveTextSanitizer.boundedText(digest.trim().toLowerCase(Locale.ROOT), 64);
        return bounded.matches("^[0-9a-f]{1,64}$") ? bounded : SensitiveTextSanitizer.sha256Hex(bounded);
    }

    private boolean downloadReady(UiE2eArtifactManifest manifest) {
        return manifest != null && artifactStorage.isDownloadReady(manifest.storageRef());
    }

    private boolean downloadReadyResponse(UiE2eArtifactManifestResponse manifest) {
        if (manifest == null || manifest.redactionFlags() == null) {
            return false;
        }
        Object ready = manifest.redactionFlags().get("rawArtifactDownloadReady");
        return Boolean.TRUE.equals(ready);
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private UiE2eRunQuery normalizeQuery(UiE2eRunQuery query) {
        return new UiE2eRunQuery(
                boundedNullable(query.projectId(), 64),
                query.sceneId(),
                query.bundleId(),
                query.status() == null ? null : normalizeRunStatus(query.status()),
                boundedNullable(query.keyword(), 128),
                Math.max(query.offset(), 0),
                Math.min(Math.max(query.limit(), 1), 100)
        );
    }

    private String boundedRequestKey(String value) {
        String requestKey = boundedNullable(value, 128);
        if (!StringUtils.hasText(requestKey)) {
            return null;
        }
        if (!requestKey.matches("^[A-Za-z0-9_.:-]{1,128}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "run requestKey 格式非法");
        }
        return requestKey;
    }

    private String compositeRequestKey(String requestKey, UiE2eRunExecutionOptions executionOptions) {
        if (!StringUtils.hasText(requestKey)) {
            return null;
        }
        if (executionOptions == null) {
            return requestKey;
        }
        String safeBase = requestKey.length() > 113 ? requestKey.substring(0, 113) : requestKey;
        String digest = SensitiveTextSanitizer.sha256Hex(executionOptions.requestSummaryKey()).substring(0, 12);
        return safeBase + "::" + digest;
    }

    private String displayRequestKey(String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return requestKey;
        }
        int separator = requestKey.lastIndexOf("::");
        if (separator <= 0 || separator >= requestKey.length() - 1) {
            return requestKey;
        }
        String suffix = requestKey.substring(separator + 2);
        return suffix.matches("^[0-9a-f]{12}$") ? requestKey.substring(0, separator) : requestKey;
    }

    /**
     * Builds one aggregate execution summary so browser-matrix, visual regression and existing control-plane flags
     * share a single persistent contract for WP7 detail, WP9 dispatch summaries and future WP10 evidence adapters.
     */
    private Map<String, Object> buildExecutionSummary(
            UiE2eRunnerPort.RunnerRunResult attempt,
            UiE2eRunExecutionOptions executionOptions,
            UUID baselineRunId,
            TestDataRunnerAccountContractResponse runnerAccountContract,
            UiE2eRunEnvironmentResolver.ResolvedUiE2eBaseUrl resolvedBaseUrl
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aggregateOnly", true);
        summary.put("runnerReady", true);
        summary.put("runnerDefaultDisabled", !properties.runnerEnabled());
        summary.put("cancelSupported", true);
        summary.put("secretRefPlaintextReturned", false);
        summary.put("baseUrlDigest", SensitiveTextSanitizer.sha256Hex(
                resolvedBaseUrl.baseUrlRef() + ":" + resolvedBaseUrl.normalizedHost() + ":" + resolvedBaseUrl.environmentKey()
        ));
        summary.put("browserTypes", executionOptions.browserTypes());
        summary.put("browserCount", executionOptions.browserTypes().size());
        summary.put("parallelExecutionEnabled", executionOptions.browserTypes().size() > 1);
        summary.put("visualRegressionEnabled", executionOptions.visualRegressionEnabled());
        if (baselineRunId != null) {
            summary.put("visualBaselineRunId", baselineRunId.toString());
        }
        if (executionOptions.visualMismatchThreshold() != null) {
            summary.put("visualMismatchThreshold", executionOptions.visualMismatchThreshold());
        }
        summary.put("accountLeaseRef", runnerAccountContract.accountLeaseRef().toString());
        summary.put("accountScopeSummaryKeys", accountSummary(runnerAccountContract).getOrDefault("scopeSummaryKeys", List.of()));
        if (attempt != null && attempt.executionSummary() != null && !attempt.executionSummary().isEmpty()) {
            SanitizedMap safeAttemptSummary = safeRunnerSummary(attempt.executionSummary());
            summary.putAll(safeAttemptSummary.value());
            summary.put("unsafeExecutionSummaryKeysFiltered", safeAttemptSummary.filteredUnsafeKeys());
        }
        return Map.copyOf(summary);
    }

    private void enrichExecutionSummary(
            Map<String, Object> executionSummary,
            UiE2eRun run,
            Map<String, Object> accountSummary,
            List<UiE2eRunStepResultResponse> stepResults,
            List<UiE2eArtifactManifestResponse> artifacts,
            UiE2eFlakyMarkResponse flakyMark
    ) {
        executionSummary.put("aggregateOnly", true);
        executionSummary.put("runnerReady", true);
        executionSummary.put("stepResultCount", stepResults.size());
        executionSummary.put("artifactManifestCount", artifacts.size());
        executionSummary.put("rawArtifactDownloadReady", artifacts.stream().anyMatch(this::downloadReadyResponse));
        executionSummary.put("secretRefPlaintextReturned", false);
        executionSummary.put("baseUrlDigest", run.baseUrlDigest());
        executionSummary.putIfAbsent("accountScopeSummaryKeys", accountSummary.getOrDefault("scopeSummaryKeys", List.of()));
        executionSummary.put("runnerDefaultDisabled", !properties.runnerEnabled());
        executionSummary.put("cancelSupported", true);
        executionSummary.put("stepStatusCounts", stepStatusCounts(stepResults));
        executionSummary.put("failureBucketCounts", failureBucketCounts(stepResults));
        executionSummary.put("artifactTypes", artifacts.stream().map(UiE2eArtifactManifestResponse::artifactType).distinct().toList());
        executionSummary.put("flakyStatus", flakyMark == null ? null : flakyMark.status());
        executionSummary.put("visualRegressionFailure", VISUAL_FAILURE_CODES.contains(String.valueOf(run.failureCode())));
    }

    private UUID resolvedBaselineRunId(UiE2eScene scene, UiE2eRunExecutionOptions executionOptions) {
        if (scene == null || executionOptions == null || !executionOptions.visualRegressionEnabled()) {
            return null;
        }
        if (executionOptions.baselineRunId() != null) {
            UiE2eRun baseline = requireRun(executionOptions.baselineRunId());
            if (!Objects.equals(baseline.sceneId(), scene.id())) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_VISUAL_BASELINE_SCOPE_DENIED");
            }
            return baseline.id();
        }
        UiE2eRunPageRequest request = new UiE2eRunPageRequest();
        request.setProjectId(scene.projectId());
        request.setSceneId(scene.id());
        request.setStatus("SUCCEEDED");
        List<UiE2eRunSummaryResponse> items = runs(request).items();
        for (UiE2eRunSummaryResponse item : items) {
            if (item == null || item.id() == null) {
                continue;
            }
            if (StringUtils.hasText(item.failureCode()) && VISUAL_FAILURE_CODES.contains(item.failureCode())) {
                continue;
            }
            return item.id();
        }
        return null;
    }

    private String safeReason(String value) {
        return SensitiveTextSanitizer.sanitizedEvidenceText(value, 512);
    }

    private String boundedNullable(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E run 数据无法解析");
        }
    }

    private String json(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E run 数据无法序列化");
        }
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP7 UI/E2E 控制面已关闭");
        }
    }

    private record SanitizedMap(
            Map<String, Object> value,
            boolean filteredUnsafeKeys
    ) {
    }

    public record DownloadableArtifact(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
