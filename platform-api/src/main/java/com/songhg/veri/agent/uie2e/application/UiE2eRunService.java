package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataRunnerAccountContractResponse;
import com.songhg.veri.agent.uie2e.application.command.CancelUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunPageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunQuery;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunExportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunSummaryResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
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

    private final UiE2eRepository repository;
    private final UiE2eActorResolver actorResolver;
    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eProperties properties;
    private final UiE2eRunnerPort runnerPort;
    private final UiE2eRunEnvironmentResolver environmentResolver;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;
    private final ObjectMapper objectMapper;

    public UiE2eRunService(
            UiE2eRepository repository,
            UiE2eActorResolver actorResolver,
            UiE2ePlatformContextClient contextClient,
            UiE2eProperties properties,
            UiE2eRunnerPort runnerPort,
            UiE2eRunEnvironmentResolver environmentResolver,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.properties = properties;
        this.runnerPort = runnerPort;
        this.environmentResolver = environmentResolver;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
        this.objectMapper = objectMapper;
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
        String requestKey = boundedRequestKey(command.requestKey());
        if (StringUtils.hasText(requestKey)) {
            return repository.runByProjectSceneAndRequestKey(scene.projectId(), scene.id(), requestKey)
                    .map(existing -> detail(existing, true))
                    .orElseGet(() -> createRun(scene, bundle, command, requestKey));
        }
        return createRun(scene, bundle, command, null);
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

    private UiE2eRunDetailResponse createRun(
            UiE2eScene scene,
            UiE2eBundle bundle,
            CreateUiE2eRunCommand command,
            String requestKey
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
                accountSummary
        ));
        if (validation != null && !validation.accepted()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, firstText(validation.errorCode(), "EXECUTION_RUNNER_NOT_READY"));
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        String traceId = TraceContext.getOrCreateTraceId();
        UiE2eRunnerPort.RunnerRunResult attempt = runnerPort.run(new UiE2eRunnerPort.RunnerRunRequest(
                runId,
                scene.id(),
                bundle.id(),
                scene.projectId(),
                resolvedBaseUrl.normalizedBaseUrl(),
                runnerAccountContract.accountLeaseRef().toString(),
                accountSummary
        ));
        UiE2eRun run = runFromAttempt(
                attempt,
                runId,
                scene,
                bundle,
                requestKey,
                runnerAccountContract,
                resolvedBaseUrl,
                actor,
                traceId,
                now
        );
        try {
            repository.insertRun(run);
        } catch (DuplicateKeyException exception) {
            if (StringUtils.hasText(requestKey)) {
                return repository.runByProjectSceneAndRequestKey(scene.projectId(), scene.id(), requestKey)
                        .map(existing -> detail(existing, true))
                        .orElseThrow(() -> exception);
            }
            throw exception;
        }
        Map<String, Object> createdAudit = new LinkedHashMap<>();
        createdAudit.put("status", run.status());
        createdAudit.put("runnerMode", run.runnerMode());
        createdAudit.put("baseUrlRefDigest", resolvedBaseUrl.summary().get("baseUrlRefDigest"));
        createdAudit.put("accountLeaseRefPresent", true);
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
            completedAudit.put("artifactManifestCount", 0);
            auditRun(run, "BLOCKED".equals(run.status()) ? "FAILED" : "SUCCESS", "ui_e2e.run.completed", completedAudit);
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
                run.requestKey(),
                run.runnerMode(),
                run.failureCode(),
                run.failureSummary(),
                run.traceId(),
                readMap(run.accountSummaryJson()),
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
        Map<String, Object> executionSummary = new LinkedHashMap<>();
        // runnerReady reflects that the control-plane contracts are live. runnerDefaultDisabled remains the switch that
        // tells the caller whether this environment will actually execute the run by default.
        executionSummary.put("aggregateOnly", true);
        executionSummary.put("runnerReady", true);
        executionSummary.put("artifactManifestCount", 0);
        executionSummary.put("rawArtifactDownloadReady", false);
        executionSummary.put("secretRefPlaintextReturned", false);
        executionSummary.put("baseUrlDigest", run.baseUrlDigest());
        executionSummary.put("accountScopeSummaryKeys", accountSummary.getOrDefault("scopeSummaryKeys", List.of()));
        executionSummary.put("runnerDefaultDisabled", !properties.runnerEnabled());
        executionSummary.put("cancelSupported", true);
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
                run.requestKey(),
                run.runnerMode(),
                run.failureCode(),
                run.failureSummary(),
                run.traceId(),
                accountSummary,
                executionSummary,
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt(),
                idempotentReplay
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
        return Set.of("DISABLED", "MANAGED", "HTTP_ADAPTER").contains(normalized) ? normalized : "DISABLED";
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
}
