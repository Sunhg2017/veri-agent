package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.command.ReviewApiAutomationScriptBundleCommand;
import com.songhg.veri.agent.apiautomation.application.command.SyncApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.parser.OpenApiParseResult;
import com.songhg.veri.agent.apiautomation.application.parser.ParsedOpenApiEndpoint;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationEndpointSnapshotResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationCaseResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationHealthResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunExportResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResultResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationScriptBundleResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncItemResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecResponse;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.AssetTestCaseService;
import com.songhg.veri.agent.asset.application.command.SyncOpenApiRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ApiAutomationService {

    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "UPLOAD", "URL");
    private static final int ERROR_SUMMARY_MAX_CHARS = 512;
    private static final int WP3_API_PATH_MAX_CHARS = 256;
    private static final int WP3_API_SUMMARY_MAX_CHARS = 256;
    private static final int WP3_API_VERSION_MAX_CHARS = 32;
    private static final int WP3_API_PAGE_SIZE = 100;
    private static final int WP3_API_PAGE_LIMIT = 20;
    private static final int GENERATION_CASE_COUNT_MAX = 5;
    private static final int GENERATION_SOURCE_TEST_CASE_MAX = 20;
    private static final int GENERATION_SOURCE_STEP_MAX = 3;
    private static final int GENERATION_SOURCE_TEXT_MAX_CHARS = 160;
    private static final int RUNNER_BASE_URL_MAX_CHARS = 512;
    private static final int RUNNER_SECRET_REF_MAX_COUNT = 10;
    private static final int RUNNER_SECRET_REF_MAX_CHARS = 256;
    private static final List<String> DIFF_STATUSES = List.of("NEW", "CHANGED", "MATCHED", "CONFLICT", "SKIPPED");
    private static final Set<String> GENERATION_MODES = Set.of("FALLBACK_ONLY", "MODEL_WITH_FALLBACK");
    private static final Set<String> COVERAGE_TYPES = Set.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final Set<String> SCRIPT_REVIEW_SUBMITTABLE_STATUSES = Set.of("DRAFT", "REJECTED");
    private static final Set<String> RUN_STATUSES = Set.of("BLOCKED", "QUEUED", "RUNNING", "PASSED", "FAILED", "TIMEOUT", "CANCELED");
    private static final Set<String> RUN_RESULT_STATUSES = Set.of("PASSED", "FAILED", "SKIPPED", "ERROR", "TIMEOUT", "BLOCKED");
    private static final Set<String> BLOCKED_RUN_TARGET_HOSTS = Set.of("localhost", "metadata.google.internal");
    private static final String SCRIPT_TEMPLATE_VERSION = "wp6-pytest-httpx-v1";
    private static final String STATIC_CHECK_PASSED = "PASSED";
    private static final String STATIC_CHECK_FAILED = "SCRIPT_STATIC_CHECK_FAILED";
    private static final String WP6_MODEL_SCHEMA_MARKER = "WP6_API_AUTOMATION_GENERATION_V1";
    private static final String MODEL_CALLER_SERVICE = "wp6-api-automation";
    private static final String MODEL_CAPABILITY_JSON = "JSON";
    private static final String DEFAULT_MODEL_SENSITIVITY_LEVEL = "INTERNAL";
    private static final List<Pattern> SENSITIVE_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._\\-]{8,}"),
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization)\\s*[:=]\\s*[^\\s,;，；]+"),
            Pattern.compile("(?i)\\b(sk|pk|rk)_[a-z0-9_-]{8,}\\b")
    );
    private static final List<Pattern> FORBIDDEN_SCRIPT_PATTERNS = List.of(
            Pattern.compile("(?m)^\\s*(import|from)\\s+(subprocess|socket|ftplib|paramiko|telnetlib|pickle|marshal)\\b"),
            Pattern.compile("\\b(os\\.system|eval|exec|__import__)\\s*\\(")
    );
    private static final Pattern IPV4_LITERAL_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern SECRET_REF_PATTERN = Pattern.compile("^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ApiAutomationRepository repository;
    private final ApiAutomationRunnerPort runnerPort;
    private final OpenApiSpecParser parser;
    private final ApiAutomationProperties properties;
    private final ApiAutomationPlatformContextClient contextClient;
    private final ApiAutomationActorResolver actorResolver;
    private final AssetApiService assetApiService;
    private final AssetTestCaseService assetTestCaseService;
    private final ModelInvocationService modelInvocationService;
    private final ApiAutomationModelOutputParser modelOutputParser;
    private final ObjectMapper objectMapper;

    public ApiAutomationService(
            ApiAutomationRepository repository,
            ApiAutomationRunnerPort runnerPort,
            OpenApiSpecParser parser,
            ApiAutomationProperties properties,
            ApiAutomationPlatformContextClient contextClient,
            ApiAutomationActorResolver actorResolver,
            AssetApiService assetApiService,
            AssetTestCaseService assetTestCaseService,
            ModelInvocationService modelInvocationService,
            ApiAutomationModelOutputParser modelOutputParser,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.runnerPort = runnerPort;
        this.parser = parser;
        this.properties = properties;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.assetApiService = assetApiService;
        this.assetTestCaseService = assetTestCaseService;
        this.modelInvocationService = modelInvocationService;
        this.modelOutputParser = modelOutputParser;
        this.objectMapper = objectMapper;
    }

    public ApiAutomationHealthResponse health() {
        return new ApiAutomationHealthResponse(
                "api-automation",
                "UP",
                List.of("3.x"),
                properties.effectiveSpecMaxBytes(),
                properties.effectiveEndpointMaxCount(),
                properties.runnerEnabled(),
                properties.effectiveRunnerTimeoutSeconds(null),
                properties.effectiveRunnerMaxCases(),
                properties.promptKey(),
                properties.modelFallbackEnabled(),
                Map.ofEntries(
                        Map.entry("parserVersion", OpenApiSpecParser.PARSER_VERSION),
                        Map.entry("sourceTypes", SOURCE_TYPES),
                        Map.entry("urlFetchEnabled", false),
                        Map.entry("rawRequestResponseStored", false),
                        Map.entry("secretExampleStored", false),
                        Map.entry("runnerDefaultDisabled", !properties.runnerEnabled()),
                        Map.entry("diffSyncReady", true),
                        Map.entry("generationReady", true),
                        Map.entry("modelGenerationReady", true),
                        Map.entry("scriptBundleReady", true),
                        Map.entry("scriptBundleReviewReady", true),
                        Map.entry("runnerRunReady", true),
                        Map.entry("runnerArtifactMaxBytes", properties.effectiveRunnerArtifactMaxBytes()),
                        Map.entry("runnerAllowedBaseUrlConfigured", !allowedBaseUrlPatterns().isEmpty()),
                        Map.entry("aggregateOnly", true)
                )
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiAutomationSpecDetailResponse createSpec(CreateApiAutomationSpecCommand command) {
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        String content = command.content() == null ? "" : command.content();
        int contentSize = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentSize > properties.effectiveSpecMaxBytes()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "OPENAPI_TOO_LARGE: OpenAPI 内容超过上限 " + properties.effectiveSpecMaxBytes() + " bytes"
            );
        }
        String sourceType = normalizeSourceType(command.sourceType());
        String specDigest = sha256(content);
        ApiAutomationSpec existing = repository.activeSpecByProjectAndDigest(projectId, specDigest).orElse(null);
        if (existing != null) {
            return specDetail(existing.id());
        }

        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationSpec spec = new ApiAutomationSpec(
                UUID.randomUUID(),
                projectId,
                sourceType,
                sanitizeSourceRef(command.sourceRef()),
                boundedText(command.name(), 128),
                boundedNullableText(command.versionLabel(), 64),
                specDigest,
                contentSize,
                "{}",
                "{}",
                "UPLOADED",
                OpenApiSpecParser.PARSER_VERSION,
                0,
                null,
                actor,
                actor,
                null,
                now,
                now
        );
        repository.insertSpec(spec);
        return parseAndPersist(markParsing(spec, now, actor), content, actor);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiAutomationSpecResponse> specs(ApiAutomationSpecPageRequest request) {
        ApiAutomationSpecQuery query = canonicalProjectQuery(request.toQuery());
        List<ApiAutomationSpecResponse> items = repository.specs(query).stream()
                .map(this::toSpecResponse)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countSpecs(query));
    }

    @Transactional(readOnly = true)
    public ApiAutomationSpecDetailResponse specDetail(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        return toDetail(spec, repository.endpointSnapshots(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiAutomationSpecDetailResponse parseSpec(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        if ("ARCHIVED".equals(spec.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档的 OpenAPI 规格不可重新解析");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationSpec parsingSpec = markParsing(spec, now, actor);
        repository.updateSpecParseResult(parsingSpec);
        return parseAndPersist(parsingSpec, parsingSpec.sanitizedSpecJson(), actor);
    }

    @Transactional
    public ApiAutomationDiffResponse diffSpec(UUID id) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        // Diff 结果是运营确认同步前的证据快照，必须落库以便审计和前端复核。
        List<ApiAutomationEndpointSnapshot> endpoints = evaluateAndPersistDiff(spec);
        audit("api_automation.api_diffed", spec, "SUCCESS", Map.of(
                "counts", countDiffStatuses(endpoints),
                "endpointCount", endpoints.size()
        ));
        return new ApiAutomationDiffResponse(spec.id(), countDiffStatuses(endpoints), endpoints.stream()
                .map(this::toEndpointResponse)
                .toList());
    }

    @Transactional
    public ApiAutomationSyncResponse syncSpec(UUID id, SyncApiAutomationSpecCommand command) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        SyncApiAutomationSpecCommand safeCommand = command == null
                ? new SyncApiAutomationSpecCommand(List.of(), true)
                : command;
        Set<UUID> selectedEndpointIds = safeCommand.endpointIdSet();
        // 同步前重新 diff，避免基于前端旧状态写入 WP3；单 endpoint 业务失败在 syncEndpoint 内收敛为明细。
        List<ApiAutomationEndpointSnapshot> diffedEndpoints = evaluateAndPersistDiff(spec);
        List<ApiAutomationSyncItemResponse> items = new ArrayList<>();
        List<ApiAutomationEndpointSnapshot> updatedEndpoints = new ArrayList<>();
        Instant now = Instant.now();
        for (ApiAutomationEndpointSnapshot endpoint : diffedEndpoints) {
            if (!selectedEndpointIds.isEmpty() && !selectedEndpointIds.contains(endpoint.id())) {
                updatedEndpoints.add(endpoint);
                continue;
            }
            SyncAttempt attempt = syncEndpoint(spec, endpoint, safeCommand, now);
            items.add(attempt.response());
            updatedEndpoints.add(attempt.snapshot());
        }
        Map<String, Integer> counts = countSyncResults(items);
        audit("api_automation.api_synced", spec, syncResult(counts), Map.of(
                "counts", counts,
                "requestedEndpointCount", selectedEndpointIds.isEmpty() ? diffedEndpoints.size() : selectedEndpointIds.size()
        ));
        return new ApiAutomationSyncResponse(spec.id(), counts, items, updatedEndpoints.stream()
                .map(this::toEndpointResponse)
                .toList());
    }

    @Transactional
    public ApiAutomationGenerationTaskDetailResponse createGenerationTask(CreateApiAutomationGenerationTaskCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "generation task 请求不能为空");
        }
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        ApiAutomationSpec spec = requireParsedSpec(command.specId());
        if (!projectId.equals(spec.projectId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "OpenAPI 规格不属于当前项目");
        }
        GenerationRequest normalized = normalizeGenerationRequest(projectId, command);
        // requestKey 面向运营台重试幂等；相同 key 只能绑定同一份规范化 payload，避免覆盖历史生成证据。
        ApiAutomationGenerationTask existing = existingGenerationTask(normalized);
        if (existing != null) {
            return generationTaskDetail(existing.id());
        }
        List<ApiAutomationEndpointSnapshot> targets = generationTargets(spec, normalized.assetApiIds());
        List<GenerationSourceTestCase> sourceTestCases = sourceTestCases(normalized, targets);
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        GenerationAttempt attempt = generationAttempt(spec, targets, normalized, sourceTestCases, actor);
        List<ApiAutomationCase> cases = attempt.cases();
        ApiAutomationGenerationTask task = new ApiAutomationGenerationTask(
                UUID.randomUUID(),
                projectId,
                spec.id(),
                normalized.requestKey(),
                normalized.requestDigest(),
                normalized.generationMode(),
                writeJson(normalized.coverageTypes()),
                "COMPLETED",
                properties.promptKey(),
                attempt.promptVersion(),
                attempt.modelInvocationId(),
                attempt.fallbackUsed(),
                targets.size(),
                cases.size(),
                writeJson(inputSummary(spec, normalized, targets, sourceTestCases, attempt)),
                attempt.errorSummary(),
                actor,
                actor,
                now,
                now
        );
        repository.insertGenerationTask(task);
        List<ApiAutomationCase> persistedCases = cases.stream()
                .map(value -> caseWithTask(value, task.id(), now))
                .toList();
        persistedCases.forEach(repository::insertAutomationCase);
        ApiAutomationScriptBundle bundle = createScriptBundle(task, persistedCases, actor, now);
        repository.insertScriptBundle(bundle);
        auditGenerationTask(task, "SUCCESS", Map.of(
                "apiCount", task.apiCount(),
                "caseCount", task.caseCount(),
                "sourceTestCaseCount", sourceTestCases.size(),
                "generationMode", task.generationMode(),
                "fallbackUsed", task.fallbackUsed(),
                "modelInvocationId", task.modelInvocationId() == null ? "" : task.modelInvocationId(),
                "promptVersion", task.promptVersion() == null ? "" : task.promptVersion(),
                "coverageTypes", normalized.coverageTypes()
        ));
        auditScriptBundle(bundle, "SUCCESS", "GENERATED", Map.of(
                "taskId", task.id().toString(),
                "fileCount", bundle.fileCount(),
                "staticCheckStatus", bundle.staticCheckStatus()
        ));
        return toGenerationTaskDetail(task, persistedCases, List.of(bundle));
    }

    @Transactional(readOnly = true)
    public ApiAutomationGenerationTaskDetailResponse generationTaskDetail(UUID id) {
        ApiAutomationGenerationTask task = repository.generationTask(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + id));
        return toGenerationTaskDetail(task, repository.automationCases(id), repository.scriptBundles(id));
    }

    @Transactional
    public ApiAutomationScriptBundleResponse generateScriptBundle(UUID taskId) {
        ApiAutomationGenerationTask task = repository.generationTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + taskId));
        List<ApiAutomationScriptBundle> existingBundles = repository.scriptBundles(taskId).stream()
                .filter(bundle -> !"ARCHIVED".equals(bundle.status()))
                .toList();
        if (!existingBundles.isEmpty()) {
            return toScriptBundleResponse(existingBundles.getFirst());
        }
        List<ApiAutomationCase> cases = repository.automationCases(taskId);
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "生成任务没有可打包的自动化用例草稿");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationScriptBundle bundle = createScriptBundle(task, cases, actor, now);
        repository.insertScriptBundle(bundle);
        auditScriptBundle(bundle, "SUCCESS", "GENERATED", Map.of(
                "taskId", task.id().toString(),
                "fileCount", bundle.fileCount(),
                "staticCheckStatus", bundle.staticCheckStatus()
        ));
        return toScriptBundleResponse(bundle);
    }

    @Transactional
    public ApiAutomationScriptBundleResponse submitScriptBundleReview(
            UUID id,
            ReviewApiAutomationScriptBundleCommand command
    ) {
        ApiAutomationScriptBundle bundle = requireScriptBundle(id);
        if (!SCRIPT_REVIEW_SUBMITTABLE_STATUSES.contains(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 DRAFT/REJECTED 脚本包可提交评审");
        }
        if (!STATIC_CHECK_PASSED.equals(bundle.staticCheckStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, STATIC_CHECK_FAILED + ": 脚本静态校验未通过");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationScriptBundle updated = scriptBundleWithReview(
                bundle,
                "REVIEWING",
                boundedNullableText(command == null ? null : command.note(), 512),
                actor,
                bundle.approvedBy(),
                now,
                null,
                null,
                actor,
                now
        );
        repository.updateScriptBundleReview(updated);
        auditScriptBundle(updated, "SUCCESS", "SUBMITTED", Map.of("notePresent", StringUtils.hasText(updated.reviewNote())));
        return toScriptBundleResponse(updated);
    }

    @Transactional
    public ApiAutomationScriptBundleResponse approveScriptBundle(
            UUID id,
            ReviewApiAutomationScriptBundleCommand command
    ) {
        ApiAutomationScriptBundle bundle = requireScriptBundle(id);
        if (!"REVIEWING".equals(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 REVIEWING 脚本包可审批通过");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationScriptBundle updated = scriptBundleWithReview(
                bundle,
                "APPROVED",
                reviewNoteOrCurrent(bundle, command),
                bundle.submittedBy(),
                actor,
                bundle.submittedAt(),
                now,
                null,
                actor,
                now
        );
        repository.updateScriptBundleReview(updated);
        auditScriptBundle(updated, "SUCCESS", "APPROVED", Map.of("notePresent", StringUtils.hasText(updated.reviewNote())));
        return toScriptBundleResponse(updated);
    }

    @Transactional
    public ApiAutomationScriptBundleResponse rejectScriptBundle(
            UUID id,
            ReviewApiAutomationScriptBundleCommand command
    ) {
        ApiAutomationScriptBundle bundle = requireScriptBundle(id);
        if (!"REVIEWING".equals(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 REVIEWING 脚本包可驳回");
        }
        String note = boundedNullableText(command == null ? null : command.note(), 512);
        if (!StringUtils.hasText(note)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回原因必填");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationScriptBundle updated = scriptBundleWithReview(
                bundle,
                "REJECTED",
                note,
                bundle.submittedBy(),
                null,
                bundle.submittedAt(),
                null,
                now,
                actor,
                now
        );
        repository.updateScriptBundleReview(updated);
        auditScriptBundle(updated, "FAILED", "REJECTED", Map.of("notePresent", true));
        return toScriptBundleResponse(updated);
    }

    @Transactional
    public ApiAutomationRunDetailResponse createRun(CreateApiAutomationRunCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "run 请求不能为空");
        }
        ApiAutomationScriptBundle bundle = requireScriptBundle(command.bundleId());
        ApiAutomationGenerationTask task = repository.generationTask(bundle.taskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + bundle.taskId()));
        List<ApiAutomationCase> cases = selectedRunCases(task, command.caseIds());
        RunTarget target = validateRunTarget(command.baseUrl());
        RunSecretRefs secretRefs = validateRunSecretRefs(command.secretRefs());
        int timeoutSeconds = properties.effectiveRunnerTimeoutSeconds(command.timeoutSeconds());
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UUID runId = UUID.randomUUID();

        /*
         * Runner admission is a control-plane safety gate: every attempt is persisted with a sanitized target digest
         * and host, while the raw baseUrl and runtime output are discarded after validation.
         */
        RunBlock block = runBlockReason(bundle, cases, target);
        if (block != null) {
            ApiAutomationRun run = newRun(
                    runId,
                    bundle,
                    command,
                    target,
                    "BLOCKED",
                    timeoutSeconds,
                    cases.size(),
                    properties.runnerEnabled() ? "NOOP" : "DISABLED",
                    block.errorCode(),
                    block.errorSummary(),
                    actor,
                    now,
                    now,
                    now
            );
            repository.insertRun(run);
            List<ApiAutomationRunResult> results = blockedRunResults(run, cases, block, now);
            results.forEach(repository::insertRunResult);
            auditRun(run, "FAILED", "BLOCKED", Map.of(
                    "errorCode", block.errorCode(),
                    "caseCount", cases.size(),
                    "baseUrlHost", target.host(),
                    "secretRefCount", secretRefs.count(),
                    "secretRefDigests", secretRefs.digests()
            ));
            return toRunDetail(run, results);
        }

        ApiAutomationRunnerPort.RunnerRunResult attempt = runnerPort.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                runId,
                bundle,
                cases,
                target.normalizedBaseUrl(),
                timeoutSeconds,
                secretRefs.digests()
        ));
        String status = normalizeRunStatus(attempt.status());
        String runnerMode = normalizeRunnerMode(attempt.runnerMode());
        Instant completedAt = Instant.now();
        ApiAutomationRun run = newRun(
                runId,
                bundle,
                command,
                target,
                status,
                timeoutSeconds,
                cases.size(),
                runnerMode,
                boundedNullableText(attempt.errorCode(), 64),
                safeRunnerErrorSummary(attempt.errorSummary(), target.normalizedBaseUrl()),
                actor,
                now,
                now,
                completedAt
        );
        repository.insertRun(run);
        List<ApiAutomationRunResult> results = runnerResults(run, cases, attempt, target.normalizedBaseUrl(), completedAt);
        results.forEach(repository::insertRunResult);
        auditRun(run, "SUCCESS", "STARTED", Map.of(
                "runnerMode", run.runnerMode(),
                "caseCount", run.caseCount(),
                "secretRefCount", secretRefs.count(),
                "secretRefDigests", secretRefs.digests()
        ));
        auditRun(run, runAuditResult(status), "COMPLETED", Map.of(
                "status", run.status(),
                "runnerMode", run.runnerMode(),
                "caseCount", run.caseCount(),
                "resultCount", results.size(),
                "secretRefCount", secretRefs.count(),
                "secretRefDigests", secretRefs.digests()
        ));
        return toRunDetail(run, results);
    }

    @Transactional(readOnly = true)
    public ApiAutomationRunDetailResponse runDetail(UUID id) {
        ApiAutomationRun run = repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化运行任务不存在: " + id));
        return toRunDetail(run, repository.runResults(id));
    }

    /**
     * Cancels only active control-plane runs. Current managed HTTP execution is synchronous, so most runs are already
     * terminal when this method is called; those calls intentionally return the existing run without mutation to keep
     * retrying clients idempotent until a future asynchronous runner can honor cancellation in-flight.
     */
    @Transactional
    public ApiAutomationRunDetailResponse cancelRun(UUID id) {
        ApiAutomationRun run = repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化运行任务不存在: " + id));
        if (!cancelableRunStatus(run.status())) {
            auditRun(run, "SUCCESS", "CANCEL_SKIPPED", Map.of(
                    "status", run.status(),
                    "terminal", true
            ));
            return toRunDetail(run, repository.runResults(id));
        }

        ApiAutomationRunnerPort.RunnerCancelResult cancelResult = runnerPort.cancel(id);
        boolean accepted = cancelResult != null && cancelResult.accepted();
        if (!accepted) {
            auditRun(run, "FAILED", "CANCEL_REJECTED", cancelAuditPayload(run, cancelResult));
            return toRunDetail(run, repository.runResults(id));
        }

        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationRun canceled = runWithCancel(
                run,
                boundedNullableText(cancelResult.errorCode(), 64),
                safeRunnerErrorSummary(cancelResult.errorSummary()),
                actor,
                now
        );
        repository.updateRunCancel(canceled);
        ApiAutomationRun persisted = repository.run(id).orElse(canceled);
        auditRun(persisted, "SUCCESS", "CANCELED", Map.of(
                "previousStatus", run.status(),
                "status", persisted.status()
        ));
        return toRunDetail(persisted, repository.runResults(id));
    }

    @Transactional
    public ApiAutomationRunExportResponse exportRun(UUID id) {
        ApiAutomationRun run = repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化运行任务不存在: " + id));
        List<ApiAutomationRunResult> results = repository.runResults(id);
        Map<String, Integer> counts = resultCounts(results);
        // 运行导出用于合规留存和运营排障，只复用脱敏摘要视图；导出动作本身也必须落审计。
        ApiAutomationRunExportResponse response = new ApiAutomationRunExportResponse(
                "wp6-run-export-v1",
                Instant.now(),
                toRunResponse(run),
                results.stream().map(this::toRunResultResponse).toList(),
                counts,
                runExportRedactionPolicy()
        );
        auditRun(run, "SUCCESS", "EXPORTED", Map.of(
                "resultCount", results.size(),
                "resultCounts", counts,
                "rawBaseUrlExported", false,
                "rawRequestResponseExported", false,
                "stdoutStderrExported", false
        ));
        return response;
    }

    private List<ApiAutomationCase> selectedRunCases(ApiAutomationGenerationTask task, List<UUID> requestedCaseIds) {
        List<ApiAutomationCase> allCases = repository.automationCases(task.id());
        if (allCases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "生成任务没有可运行的自动化用例");
        }
        List<UUID> requested = normalizedUuidList(requestedCaseIds);
        List<ApiAutomationCase> selected = requested.isEmpty()
                ? allCases
                : allCases.stream().filter(automationCase -> requested.contains(automationCase.id())).toList();
        if (!requested.isEmpty()) {
            Set<UUID> found = selected.stream().map(ApiAutomationCase::id).collect(Collectors.toSet());
            if (!found.containsAll(requested)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "caseIds 必须来自脚本包关联的生成任务");
            }
        }
        if (selected.size() > properties.effectiveRunnerMaxCases()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "RUNNER_CASE_LIMIT_EXCEEDED: 单次运行最多 " + properties.effectiveRunnerMaxCases() + " 条用例"
            );
        }
        return selected;
    }

    /**
     * Validates secret references as runtime-only inputs. Full secretRef values are never persisted, exported or passed
     * to audit; only deterministic digests are forwarded so future runner adapters can correlate injected credentials
     * without leaking references or plaintext.
     */
    private RunSecretRefs validateRunSecretRefs(List<String> rawSecretRefs) {
        if (rawSecretRefs == null || rawSecretRefs.isEmpty()) {
            return new RunSecretRefs(0, List.of());
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawSecretRef : rawSecretRefs) {
            String secretRef = rawSecretRef == null ? null : rawSecretRef.trim();
            if (!StringUtils.hasText(secretRef)) {
                continue;
            }
            if (secretRef.length() > RUNNER_SECRET_REF_MAX_CHARS) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "secretRefs 单个引用最多 " + RUNNER_SECRET_REF_MAX_CHARS + " 字符"
                );
            }
            if (!SECRET_REF_PATTERN.matcher(secretRef).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "secretRefs 必须使用 secret:// 引用");
            }
            normalized.add(secretRef);
        }
        if (normalized.size() > RUNNER_SECRET_REF_MAX_COUNT) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "secretRefs 单次运行最多 " + RUNNER_SECRET_REF_MAX_COUNT + " 个"
            );
        }
        return new RunSecretRefs(
                normalized.size(),
                normalized.stream().map(secretRef -> "sha256:" + sha256(secretRef)).toList()
        );
    }

    /**
     * Normalizes the runner base URL before any persistence. The raw URL is used only for the runner adapter call; the
     * database receives a digest and host so queryability and audit correlation do not leak path, query or credentials.
     */
    private RunTarget validateRunTarget(String rawBaseUrl) {
        String bounded = boundedNullableText(rawBaseUrl, RUNNER_BASE_URL_MAX_CHARS);
        if (!StringUtils.hasText(bounded)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必填");
        }
        URI uri;
        try {
            uri = new URI(bounded);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必须是合法 HTTP/HTTPS URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 仅支持 http/https");
        }
        if (StringUtils.hasText(uri.getRawUserInfo()) || StringUtils.hasText(uri.getRawQuery())
                || StringUtils.hasText(uri.getRawFragment())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 不允许携带 userInfo/query/fragment");
        }
        String host = normalizedHost(uri.getHost());
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "baseUrl 必须包含 host");
        }
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "";
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        int port = uri.getPort();
        String authority = port > 0 ? host + ":" + port : host;
        String normalized = scheme + "://" + authority + path;
        return new RunTarget(normalized, host, sha256(normalized), blockedTargetHost(host), allowedTargetHost(host));
    }

    private String normalizedHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        try {
            return IDN.toASCII(host.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private boolean blockedTargetHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_RUN_TARGET_HOSTS.contains(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        if ("169.254.169.254".equals(normalized) || "::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized)) {
            return true;
        }
        if (IPV4_LITERAL_PATTERN.matcher(normalized).matches()) {
            return privateIpv4(normalized);
        }
        return normalized.startsWith("[") || normalized.endsWith(".local");
    }

    private boolean privateIpv4(String host) {
        String[] parts = host.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        if (first == 10 || first == 127 || first == 0 || first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        return first == 192 && second == 168;
    }

    private boolean allowedTargetHost(String host) {
        List<String> patterns = allowedBaseUrlPatterns();
        if (patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> hostMatchesPattern(host, pattern));
    }

    private List<String> allowedBaseUrlPatterns() {
        if (!StringUtils.hasText(properties.runnerAllowedBaseUrlPatterns())) {
            return List.of();
        }
        return List.of(properties.runnerAllowedBaseUrlPatterns().split(",")).stream()
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean hostMatchesPattern(String host, String pattern) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedPattern = pattern;
        if (normalizedPattern.startsWith("http://") || normalizedPattern.startsWith("https://")) {
            try {
                normalizedPattern = normalizedHost(new URI(normalizedPattern).getHost());
            } catch (URISyntaxException exception) {
                return false;
            }
        }
        if (normalizedPattern.startsWith("*.")) {
            String suffix = normalizedPattern.substring(1);
            return normalizedHost.endsWith(suffix) && normalizedHost.length() > suffix.length();
        }
        return normalizedHost.equals(normalizedPattern);
    }

    private RunBlock runBlockReason(ApiAutomationScriptBundle bundle, List<ApiAutomationCase> cases, RunTarget target) {
        if (!"APPROVED".equals(bundle.status())) {
            return new RunBlock("RUNNER_BUNDLE_NOT_APPROVED", "脚本包未审批通过，不能运行");
        }
        if (!STATIC_CHECK_PASSED.equals(bundle.staticCheckStatus())) {
            return new RunBlock(STATIC_CHECK_FAILED, "脚本静态校验未通过，不能运行");
        }
        if (target.blocked() || properties.runnerEnabled() && !target.allowed()) {
            return new RunBlock("RUNNER_TARGET_BLOCKED", "runner 目标地址未通过安全策略");
        }
        if (!properties.runnerEnabled()) {
            return new RunBlock("RUNNER_DISABLED", "runner 默认关闭，未执行外部请求");
        }
        ApiAutomationRunnerPort.RunnerValidation validation = runnerPort.validateBundle(bundle);
        if (!validation.accepted()) {
            return new RunBlock(
                    StringUtils.hasText(validation.errorCode())
                            ? boundedText(validation.errorCode(), 64)
                            : "RUNNER_FAILED",
                    StringUtils.hasText(validation.errorSummary())
                            ? safeRunnerErrorSummary(validation.errorSummary())
                            : "runner 校验失败"
            );
        }
        if (cases.isEmpty()) {
            return new RunBlock("RUNNER_CASE_NOT_FOUND", "没有可运行的用例");
        }
        return null;
    }

    private ApiAutomationRun newRun(
            UUID runId,
            ApiAutomationScriptBundle bundle,
            CreateApiAutomationRunCommand command,
            RunTarget target,
            String status,
            int timeoutSeconds,
            int caseCount,
            String runnerMode,
            String errorCode,
            String errorSummary,
            String actor,
            Instant now,
            Instant startedAt,
            Instant completedAt
    ) {
        return new ApiAutomationRun(
                runId,
                bundle.projectId(),
                bundle.id(),
                boundedNullableText(command.environmentId(), 128),
                target.digest(),
                target.host(),
                status,
                timeoutSeconds,
                caseCount,
                TraceContext.getOrCreateTraceId(),
                runnerMode,
                errorCode,
                errorSummary,
                actor,
                actor,
                startedAt,
                completedAt,
                now,
                completedAt == null ? now : completedAt
        );
    }

    private List<ApiAutomationRunResult> blockedRunResults(
            ApiAutomationRun run,
            List<ApiAutomationCase> cases,
            RunBlock block,
            Instant now
    ) {
        return cases.stream()
                .map(automationCase -> new ApiAutomationRunResult(
                        UUID.randomUUID(),
                        run.id(),
                        automationCase.id(),
                        "BLOCKED",
                        0,
                        writeJson(Map.of(
                                "aggregateOnly", true,
                                "rawRequestResponseStored", false,
                                "reason", block.errorCode()
                        )),
                        block.errorCode(),
                        block.errorSummary(),
                        now,
                        now
                ))
                .toList();
    }

    private List<ApiAutomationRunResult> runnerResults(
            ApiAutomationRun run,
            List<ApiAutomationCase> cases,
            ApiAutomationRunnerPort.RunnerRunResult attempt,
            String normalizedBaseUrl,
            Instant now
    ) {
        Map<UUID, ApiAutomationRunnerPort.RunnerCaseResult> resultByCaseId = attempt.caseResults() == null
                ? Map.of()
                : attempt.caseResults().stream()
                .filter(result -> result.caseId() != null)
                .collect(Collectors.toMap(
                        ApiAutomationRunnerPort.RunnerCaseResult::caseId,
                        result -> result,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        return cases.stream()
                .map(automationCase -> {
                    ApiAutomationRunnerPort.RunnerCaseResult runnerResult = resultByCaseId.get(automationCase.id());
                    String status = runnerResult == null ? runResultStatusFromRun(run.status()) : normalizeRunResultStatus(runnerResult.status());
                    return new ApiAutomationRunResult(
                            UUID.randomUUID(),
                            run.id(),
                            automationCase.id(),
                            status,
                            runnerResult == null ? 0 : Math.max(0, runnerResult.durationMs()),
                            safeAssertionSummary(runnerResult == null ? null : runnerResult.assertionSummaryJson()),
                            runnerResult == null ? run.errorCode() : boundedNullableText(runnerResult.errorCode(), 64),
                            runnerResult == null
                                    ? run.errorSummary()
                                    : safeRunnerErrorSummary(runnerResult.errorSummary(), normalizedBaseUrl),
                            now,
                            now
                    );
                })
                .toList();
    }

    private String safeAssertionSummary(String value) {
        Map<String, Object> summary = readSummary(value);
        if (summary.isEmpty()) {
            return writeJson(Map.of("aggregateOnly", true, "rawRequestResponseStored", false));
        }
        /*
         * Runner adapters are an external trust boundary. Their summaries are useful for diagnosis, but must be
         * reduced to aggregate, recursively redacted evidence before persistence or export.
         */
        Map<String, Object> sanitized = new LinkedHashMap<>();
        summary.forEach((key, summaryValue) -> sanitized.put(key, sanitizeRunnerSummaryValue(key, summaryValue)));
        sanitized.put("aggregateOnly", true);
        sanitized.put("rawRequestResponseStored", false);
        sanitized.put("secretValuesStored", false);
        return writeJson(sanitized);
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeRunnerSummaryValue(String key, Object value) {
        if (sensitiveRunnerSummaryKey(key)) {
            return "[REDACTED]";
        }
        if (value instanceof String text) {
            return safeSourceText(text, ERROR_SUMMARY_MAX_CHARS);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> sanitized.put(
                    nestedKey == null ? "" : nestedKey.toString(),
                    sanitizeRunnerSummaryValue(nestedKey == null ? "" : nestedKey.toString(), nestedValue)
            ));
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> sanitizeRunnerSummaryValue("", item)).toList();
        }
        return value;
    }

    private boolean sensitiveRunnerSummaryKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("api-key");
    }

    private String safeRunnerErrorSummary(String value) {
        return safeRunnerErrorSummary(value, null);
    }

    private String safeRunnerErrorSummary(String value, String normalizedBaseUrl) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value;
        if (StringUtils.hasText(normalizedBaseUrl)) {
            sanitized = sanitized.replace(normalizedBaseUrl, "[REDACTED_BASE_URL]");
        }
        return safeSourceText(sanitized, ERROR_SUMMARY_MAX_CHARS);
    }

    private String normalizeRunStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "FAILED";
        return RUN_STATUSES.contains(normalized) ? normalized : "FAILED";
    }

    private String normalizeRunResultStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "ERROR";
        return RUN_RESULT_STATUSES.contains(normalized) ? normalized : "ERROR";
    }

    private String runResultStatusFromRun(String runStatus) {
        return switch (runStatus) {
            case "PASSED" -> "PASSED";
            case "TIMEOUT" -> "TIMEOUT";
            case "BLOCKED" -> "BLOCKED";
            default -> "ERROR";
        };
    }

    private String normalizeRunnerMode(String runnerMode) {
        String normalized = StringUtils.hasText(runnerMode) ? runnerMode.trim().toUpperCase(Locale.ROOT) : "NOOP";
        return Set.of("DISABLED", "NOOP", "MANAGED", "EXTERNAL").contains(normalized) ? normalized : "NOOP";
    }

    private String runAuditResult(String status) {
        return "PASSED".equals(status) ? "SUCCESS" : "FAILED";
    }

    private boolean cancelableRunStatus(String status) {
        return Set.of("QUEUED", "RUNNING").contains(status);
    }

    private ApiAutomationRun runWithCancel(
            ApiAutomationRun run,
            String errorCode,
            String errorSummary,
            String actor,
            Instant now
    ) {
        return new ApiAutomationRun(
                run.id(),
                run.projectId(),
                run.bundleId(),
                run.environmentId(),
                run.baseUrlDigest(),
                run.baseUrlHost(),
                "CANCELED",
                run.timeoutSeconds(),
                run.caseCount(),
                run.traceId(),
                run.runnerMode(),
                StringUtils.hasText(errorCode) ? errorCode : "RUNNER_CANCELED",
                errorSummary,
                run.createdBy(),
                actor,
                run.startedAt(),
                now,
                run.createdAt(),
                now
        );
    }

    private Map<String, Object> cancelAuditPayload(
            ApiAutomationRun run,
            ApiAutomationRunnerPort.RunnerCancelResult cancelResult
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", run.status());
        payload.put("accepted", false);
        if (cancelResult != null) {
            String errorCode = boundedNullableText(cancelResult.errorCode(), 64);
            String errorSummary = safeRunnerErrorSummary(cancelResult.errorSummary());
            if (errorCode != null) {
                payload.put("errorCode", errorCode);
            }
            if (errorSummary != null) {
                payload.put("errorSummary", errorSummary);
            }
        }
        return payload;
    }

    /**
     * Builds a reviewable script bundle from already persisted case drafts. The source strings are used only to compute
     * file digests and static-check evidence; persistence keeps file tree and dependency summaries so request bodies,
     * response bodies and secret-bearing runtime configuration never become bundle metadata.
     */
    private ApiAutomationScriptBundle createScriptBundle(
            ApiAutomationGenerationTask task,
            List<ApiAutomationCase> cases,
            String actor,
            Instant now
    ) {
        List<ScriptFile> files = scriptFiles(cases);
        Map<String, Object> fileTreeSummary = fileTreeSummary(task, cases, files);
        Map<String, Object> dependencySummary = dependencySummary();
        StaticCheckResult staticCheck = staticCheck(files);
        String bundleDigest = sha256(writeJson(Map.of(
                "templateVersion", SCRIPT_TEMPLATE_VERSION,
                "taskId", task.id().toString(),
                "fileTreeSummary", fileTreeSummary,
                "dependencySummary", dependencySummary
        )));
        return new ApiAutomationScriptBundle(
                UUID.randomUUID(),
                task.projectId(),
                task.id(),
                "DRAFT",
                bundleDigest,
                files.size(),
                writeJson(fileTreeSummary),
                writeJson(dependencySummary),
                staticCheck.status(),
                writeJson(staticCheck.summary()),
                null,
                null,
                null,
                null,
                null,
                null,
                actor,
                actor,
                now,
                now
        );
    }

    private List<ScriptFile> scriptFiles(List<ApiAutomationCase> cases) {
        List<ApiAutomationCase> sortedCases = cases.stream()
                .sorted((left, right) -> {
                    int pathCompare = left.path().compareTo(right.path());
                    if (pathCompare != 0) {
                        return pathCompare;
                    }
                    int coverageCompare = left.coverageType().compareTo(right.coverageType());
                    return coverageCompare != 0 ? coverageCompare : left.id().compareTo(right.id());
                })
                .toList();
        return List.of(
                new ScriptFile("pyproject.toml", "PYPROJECT", pyprojectToml(sortedCases.size())),
                new ScriptFile("tests/__init__.py", "PYTHON_PACKAGE", ""),
                new ScriptFile("tests/conftest.py", "PYTEST_FIXTURE", conftestPy()),
                new ScriptFile("tests/helpers.py", "ASSERTION_HELPER", helpersPy()),
                new ScriptFile("tests/test_generated_api.py", "PYTEST_CASES", generatedTestPy(sortedCases)),
                new ScriptFile("README.md", "RUNBOOK", bundleReadme(sortedCases.size()))
        );
    }

    private String pyprojectToml(int caseCount) {
        return """
                [project]
                name = "wp6-api-automation-bundle"
                version = "0.1.0"
                description = "Generated WP6 API automation bundle metadata"
                requires-python = ">=3.11"
                dependencies = [
                    "pytest>=8,<9",
                    "httpx>=0.27,<1"
                ]

                [tool.wp6]
                template_version = "%s"
                case_count = %d
                """.formatted(SCRIPT_TEMPLATE_VERSION, caseCount);
    }

    private String conftestPy() {
        return """
                import pytest


                def pytest_addoption(parser):
                    parser.addoption("--base-url", action="store", default="http://127.0.0.1:8080")


                @pytest.fixture()
                def base_url(pytestconfig):
                    return pytestconfig.getoption("--base-url").rstrip("/")


                @pytest.fixture()
                def default_headers():
                    return {}
                """;
    }

    private String helpersPy() {
        return """
                def assert_status(response, expected_status):
                    assert response.status_code == expected_status


                def assert_response_bounded(response):
                    assert len(response.content) <= 1048576
                """;
    }

    private String generatedTestPy(List<ApiAutomationCase> cases) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                import httpx
                import pytest

                from tests.helpers import assert_response_bounded, assert_status


                CASES = [
                """);
        for (ApiAutomationCase automationCase : cases) {
            builder.append("    {\n")
                    .append("        \"case_id\": ").append(pythonString(automationCase.id().toString())).append(",\n")
                    .append("        \"title\": ").append(pythonString(automationCase.title())).append(",\n")
                    .append("        \"method\": ").append(pythonString(automationCase.httpMethod())).append(",\n")
                    .append("        \"path\": ").append(pythonString(automationCase.path())).append(",\n")
                    .append("        \"coverage_type\": ").append(pythonString(automationCase.coverageType())).append(",\n")
                    .append("        \"expected_status\": ").append(automationCase.expectedStatus()).append(",\n")
                    .append("    },\n");
        }
        builder.append("""
                ]


                @pytest.mark.parametrize("case", CASES, ids=[item["title"] for item in CASES])
                def test_generated_api_contract(base_url, default_headers, case):
                    url = f"{base_url}{case['path']}"
                    with httpx.Client(headers=default_headers, timeout=10.0) as client:
                        response = client.request(case["method"], url)
                    assert_status(response, case["expected_status"])
                    assert_response_bounded(response)
                """);
        return builder.toString();
    }

    private String bundleReadme(int caseCount) {
        return """
                # WP6 API Automation Bundle

                Template: %s
                Cases: %d

                Runtime base URL and headers are supplied by the controlled runner. The bundle metadata does not store
                raw request bodies, response bodies, tokens, passwords or environment variable values.
                """.formatted(SCRIPT_TEMPLATE_VERSION, caseCount);
    }

    private Map<String, Object> fileTreeSummary(
            ApiAutomationGenerationTask task,
            List<ApiAutomationCase> cases,
            List<ScriptFile> files
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateVersion", SCRIPT_TEMPLATE_VERSION);
        summary.put("taskId", task.id().toString());
        summary.put("caseCount", cases.size());
        summary.put("caseIdsDigest", sha256(cases.stream()
                .map(value -> value.id().toString())
                .sorted()
                .collect(Collectors.joining(","))));
        summary.put("files", files.stream().map(file -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", file.path());
            item.put("kind", file.kind());
            item.put("digest", sha256(file.content()));
            item.put("lineCount", lineCount(file.content()));
            item.put("pythonFile", file.path().endsWith(".py"));
            return item;
        }).toList());
        summary.put("rawSourceStored", false);
        summary.put("secretValuesStored", false);
        summary.put("aggregateOnly", true);
        return summary;
    }

    private Map<String, Object> dependencySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runtime", "python>=3.11");
        summary.put("packageManager", "pip");
        summary.put("dependencies", List.of(
                Map.of("name", "pytest", "versionRange", ">=8,<9"),
                Map.of("name", "httpx", "versionRange", ">=0.27,<1")
        ));
        summary.put("networkAccessDuringStaticCheck", false);
        summary.put("secretValuesStored", false);
        summary.put("aggregateOnly", true);
        return summary;
    }

    /**
     * Static checks intentionally run without executing Python or touching the network. Because WP6 M5 stores only
     * generated template summaries, this method validates the generated source before it is reduced to digests and
     * blocks imports or literals that would make a future runner unsafe by default.
     */
    private StaticCheckResult staticCheck(List<ScriptFile> files) {
        List<String> violations = new ArrayList<>();
        int pythonFileCount = 0;
        for (ScriptFile file : files) {
            if (!file.path().endsWith(".py")) {
                continue;
            }
            pythonFileCount++;
            if (!balancedTemplateDelimiters(file.content())) {
                violations.add(file.path() + ":PYTHON_TEMPLATE_SYNTAX");
            }
            for (Pattern pattern : FORBIDDEN_SCRIPT_PATTERNS) {
                if (pattern.matcher(file.content()).find()) {
                    violations.add(file.path() + ":FORBIDDEN_IMPORT_OR_CALL");
                    break;
                }
            }
            if (containsSensitiveText(file.content())) {
                violations.add(file.path() + ":HARDCODED_SECRET_PATTERN");
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateVersion", SCRIPT_TEMPLATE_VERSION);
        summary.put("pythonSyntax", violations.stream().noneMatch(value -> value.endsWith("PYTHON_TEMPLATE_SYNTAX"))
                ? "PASSED"
                : "FAILED");
        summary.put("forbiddenImports", violations.stream()
                .filter(value -> value.endsWith("FORBIDDEN_IMPORT_OR_CALL"))
                .count());
        summary.put("secretPatternHits", violations.stream()
                .filter(value -> value.endsWith("HARDCODED_SECRET_PATTERN"))
                .count());
        summary.put("pythonFileCount", pythonFileCount);
        summary.put("violations", violations);
        summary.put("networkAccessDuringStaticCheck", false);
        summary.put("aggregateOnly", true);
        return new StaticCheckResult(violations.isEmpty() ? STATIC_CHECK_PASSED : STATIC_CHECK_FAILED, summary);
    }

    private boolean balancedTemplateDelimiters(String content) {
        int round = 0;
        int square = 0;
        int curly = 0;
        for (int index = 0; index < content.length(); index++) {
            char value = content.charAt(index);
            if (value == '(') {
                round++;
            } else if (value == ')') {
                round--;
            } else if (value == '[') {
                square++;
            } else if (value == ']') {
                square--;
            } else if (value == '{') {
                curly++;
            } else if (value == '}') {
                curly--;
            }
            if (round < 0 || square < 0 || curly < 0) {
                return false;
            }
        }
        return round == 0 && square == 0 && curly == 0;
    }

    private int lineCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return content.split("\\R", -1).length;
    }

    private String pythonString(String value) {
        String escaped = nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    private boolean containsSensitiveText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return SENSITIVE_TEXT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    private GenerationRequest normalizeGenerationRequest(
            String projectId,
            CreateApiAutomationGenerationTaskCommand command
    ) {
        List<String> coverageTypes = normalizeCoverageTypes(command.coverageTypes());
        String generationMode = normalizeGenerationMode(command.generationMode());
        int caseCountPerApi = normalizeCaseCountPerApi(command.caseCountPerApi(), coverageTypes.size());
        List<UUID> assetApiIds = normalizedUuidList(command.assetApiIds());
        List<UUID> assetTestCaseIds = normalizedUuidList(command.assetTestCaseIds());
        if (assetTestCaseIds.size() > GENERATION_SOURCE_TEST_CASE_MAX) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "assetTestCaseIds 最多支持 " + GENERATION_SOURCE_TEST_CASE_MAX + " 个"
            );
        }
        String requestKey = boundedNullableText(command.requestKey(), 128);
        Map<String, Object> digestPayload = new LinkedHashMap<>();
        digestPayload.put("projectId", projectId);
        digestPayload.put("specId", command.specId().toString());
        digestPayload.put("assetApiIds", uuidStrings(assetApiIds));
        digestPayload.put("assetTestCaseIds", uuidStrings(assetTestCaseIds));
        digestPayload.put("coverageTypes", coverageTypes);
        digestPayload.put("generationMode", generationMode);
        digestPayload.put("caseCountPerApi", caseCountPerApi);
        String requestDigest = sha256(writeJson(digestPayload));
        return new GenerationRequest(
                projectId,
                command.specId(),
                requestKey,
                requestDigest,
                generationMode,
                coverageTypes,
                assetApiIds,
                assetTestCaseIds,
                caseCountPerApi
        );
    }

    private ApiAutomationGenerationTask existingGenerationTask(GenerationRequest request) {
        if (StringUtils.hasText(request.requestKey())) {
            ApiAutomationGenerationTask existing = repository
                    .generationTaskByProjectAndKey(request.projectId(), request.requestKey())
                    .orElse(null);
            if (existing != null && !request.requestDigest().equals(existing.requestDigest())) {
                throw new BusinessException(ErrorCode.CONFLICT, "generation requestKey 已被不同 payload 使用");
            }
            if (existing != null) {
                return existing;
            }
        }
        return repository
                .generationTaskByProjectAndDigest(request.projectId(), request.requestDigest())
                .orElse(null);
    }

    private List<ApiAutomationEndpointSnapshot> generationTargets(
            ApiAutomationSpec spec,
            List<UUID> requestedAssetApiIds
    ) {
        Set<UUID> requested = Set.copyOf(requestedAssetApiIds);
        List<ApiAutomationEndpointSnapshot> targets = repository.endpointSnapshots(spec.id()).stream()
                .filter(endpoint -> endpoint.assetApiId() != null)
                .filter(endpoint -> requested.isEmpty() || requested.contains(endpoint.assetApiId()))
                .toList();
        if (!requested.isEmpty()) {
            Set<UUID> found = targets.stream()
                    .map(ApiAutomationEndpointSnapshot::assetApiId)
                    .collect(Collectors.toSet());
            if (!found.containsAll(requested)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "assetApiIds 必须来自当前 OpenAPI 规格已同步的 endpoint");
            }
        }
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "没有可生成的已同步 API endpoint，请先执行 diff/sync");
        }
        return targets;
    }

    private List<GenerationSourceTestCase> sourceTestCases(
            GenerationRequest request,
            List<ApiAutomationEndpointSnapshot> targets
    ) {
        if (request.assetTestCaseIds().isEmpty()) {
            return List.of();
        }
        Set<UUID> targetAssetApiIds = targets.stream()
                .map(ApiAutomationEndpointSnapshot::assetApiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<GenerationSourceTestCase> sourceCases = new ArrayList<>();
        for (UUID assetTestCaseId : request.assetTestCaseIds()) {
            TestCaseResponse testCase = assetTestCaseService.getTestCase(assetTestCaseId);
            if (!request.projectId().equals(testCase.projectId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "测试用例不属于当前项目: " + assetTestCaseId);
            }
            if (testCase.apiId() != null && !targetAssetApiIds.contains(testCase.apiId())) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "assetTestCaseIds 必须关联当前生成范围内的已同步 API: " + assetTestCaseId
                );
            }
            sourceCases.add(sourceTestCaseSummary(testCase));
        }
        return sourceCases;
    }

    private GenerationAttempt generationAttempt(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> targets,
            GenerationRequest request,
            List<GenerationSourceTestCase> sourceTestCases,
            String actor
    ) {
        /*
         * MODEL_WITH_FALLBACK is intentionally model-first but never model-only in the default WP6 path. WP2 policy,
         * provider failures and schema violations must remain visible on the task while deterministic drafts keep the
         * reviewer workflow available.
         */
        if ("FALLBACK_ONLY".equals(request.generationMode())) {
            return fallbackGenerationAttempt(spec, targets, request, sourceTestCases, null, "DETERMINISTIC_MODE");
        }
        try {
            return modelGenerationAttempt(spec, targets, request, sourceTestCases, actor);
        } catch (ApiAutomationModelGenerationException exception) {
            if (!properties.modelFallbackEnabled()) {
                throw unwrapModelGenerationException(exception);
            }
            return fallbackGenerationAttempt(spec, targets, request, sourceTestCases,
                    exception.response(), modelFallbackReason(exception));
        } catch (RuntimeException exception) {
            if (!properties.modelFallbackEnabled()) {
                throw exception;
            }
            return fallbackGenerationAttempt(spec, targets, request, sourceTestCases,
                    null, modelFallbackReason(exception));
        }
    }

    private GenerationAttempt modelGenerationAttempt(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> targets,
            GenerationRequest request,
            List<GenerationSourceTestCase> sourceTestCases,
            String actor
    ) {
        ModelInvocationResult response = null;
        try {
            response = modelInvocationService.invoke(new ModelInvocationCommand(
                    request.projectId(),
                    null,
                    null,
                    properties.promptKey(),
                    Map.of("schemaMarker", WP6_MODEL_SCHEMA_MARKER),
                    List.of(new ChatMessage("user", modelGenerationPayload(spec, targets, request, sourceTestCases))),
                    null,
                    null,
                    false,
                    DEFAULT_MODEL_SENSITIVITY_LEVEL,
                    MODEL_CAPABILITY_JSON
            ), new ServicePrincipal(MODEL_CALLER_SERVICE, actor));
            List<ApiAutomationModelOutputParser.ModelGeneratedApiCase> generatedCases =
                    modelOutputParser.parse(response.content());
            List<ApiAutomationCase> cases = casesFromModelOutput(spec, targets, request, sourceTestCases, generatedCases);
            return new GenerationAttempt(
                    cases,
                    response.fallbackUsed(),
                    null,
                    response.invocationId().toString(),
                    response.promptVersion() == null ? null : response.promptVersion().toString(),
                    response.providerName(),
                    response.modelName(),
                    response.fallbackUsed(),
                    true,
                    null
            );
        } catch (RuntimeException exception) {
            throw new ApiAutomationModelGenerationException(response, exception);
        }
    }

    private GenerationAttempt fallbackGenerationAttempt(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> targets,
            GenerationRequest request,
            List<GenerationSourceTestCase> sourceTestCases,
            ModelInvocationResult response,
            String fallbackReason
    ) {
        List<ApiAutomationCase> cases = fallbackCases(spec, targets, request, sourceTestCases);
        return new GenerationAttempt(
                cases,
                true,
                fallbackReason,
                response == null ? null : response.invocationId().toString(),
                response == null || response.promptVersion() == null ? null : response.promptVersion().toString(),
                response == null ? null : response.providerName(),
                response == null ? null : response.modelName(),
                response != null && response.fallbackUsed(),
                false,
                "DETERMINISTIC_MODE".equals(fallbackReason)
                        ? null
                        : boundedNullableText(fallbackReason, ERROR_SUMMARY_MAX_CHARS)
        );
    }

    private List<ApiAutomationCase> casesFromModelOutput(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> targets,
            GenerationRequest request,
            List<GenerationSourceTestCase> sourceTestCases,
            List<ApiAutomationModelOutputParser.ModelGeneratedApiCase> generatedCases
    ) {
        Map<UUID, ApiAutomationEndpointSnapshot> endpointByAssetApiId = targets.stream()
                .filter(endpoint -> endpoint.assetApiId() != null)
                .collect(Collectors.toMap(
                        ApiAutomationEndpointSnapshot::assetApiId,
                        endpoint -> endpoint,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        Map<String, ApiAutomationEndpointSnapshot> endpointByMethodPath = targets.stream()
                .collect(Collectors.toMap(
                        endpoint -> assetKey(endpoint.httpMethod(), endpoint.path()),
                        endpoint -> endpoint,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        Map<UUID, UUID> sourceCaseIdByAssetApiId = sourceTestCases.stream()
                .filter(sourceCase -> sourceCase.apiId() != null)
                .collect(Collectors.toMap(
                        GenerationSourceTestCase::apiId,
                        GenerationSourceTestCase::id,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        Map<UUID, Integer> countByEndpoint = new LinkedHashMap<>();
        List<ApiAutomationCase> cases = new ArrayList<>();
        for (ApiAutomationModelOutputParser.ModelGeneratedApiCase generatedCase : generatedCases) {
            // Model output is untrusted: persist only cases that still match the caller-selected coverage and endpoints.
            if (!request.coverageTypes().contains(generatedCase.coverageType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出 coverageType 不在请求范围: "
                        + generatedCase.coverageType());
            }
            ApiAutomationEndpointSnapshot endpoint = resolveGeneratedEndpoint(
                    generatedCase,
                    endpointByAssetApiId,
                    endpointByMethodPath
            );
            int currentCount = countByEndpoint.getOrDefault(endpoint.id(), 0);
            if (currentCount >= request.caseCountPerApi()) {
                continue;
            }
            UUID sourceTestCaseId = endpoint.assetApiId() == null ? null : sourceCaseIdByAssetApiId.get(endpoint.assetApiId());
            cases.add(modelCase(spec, endpoint, generatedCase, sourceTestCaseId));
            countByEndpoint.put(endpoint.id(), currentCount + 1);
        }
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出未匹配当前生成范围内的 API");
        }
        return cases;
    }

    private ApiAutomationEndpointSnapshot resolveGeneratedEndpoint(
            ApiAutomationModelOutputParser.ModelGeneratedApiCase generatedCase,
            Map<UUID, ApiAutomationEndpointSnapshot> endpointByAssetApiId,
            Map<String, ApiAutomationEndpointSnapshot> endpointByMethodPath
    ) {
        ApiAutomationEndpointSnapshot endpoint = generatedCase.assetApiId() == null
                ? null
                : endpointByAssetApiId.get(generatedCase.assetApiId());
        if (endpoint == null) {
            endpoint = endpointByMethodPath.get(assetKey(generatedCase.method(), generatedCase.path()));
        }
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出 API 不在当前生成范围: "
                    + generatedCase.method() + " " + generatedCase.path());
        }
        if (generatedCase.assetApiId() != null && !generatedCase.assetApiId().equals(endpoint.assetApiId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出 assetApiId 与 endpoint 不匹配");
        }
        if (!endpoint.httpMethod().equals(generatedCase.method()) || !endpoint.path().equals(generatedCase.path())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出 method/path 与 endpoint 不匹配");
        }
        return endpoint;
    }

    private ApiAutomationCase modelCase(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint,
            ApiAutomationModelOutputParser.ModelGeneratedApiCase generatedCase,
            UUID sourceTestCaseId
    ) {
        return new ApiAutomationCase(
                UUID.randomUUID(),
                null,
                spec.projectId(),
                spec.id(),
                endpoint.id(),
                endpoint.assetApiId(),
                sourceTestCaseId,
                boundedText(generatedCase.title(), 256),
                endpoint.httpMethod(),
                endpoint.path(),
                generatedCase.coverageType(),
                generatedCase.expectedStatus(),
                writeJson(modelAssertionSummary(endpoint, generatedCase)),
                writeJson(modelRequestTemplate(endpoint, generatedCase)),
                "MODEL",
                "DRAFT",
                null,
                null
        );
    }

    private Map<String, Object> modelAssertionSummary(
            ApiAutomationEndpointSnapshot endpoint,
            ApiAutomationModelOutputParser.ModelGeneratedApiCase generatedCase
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("coverageType", generatedCase.coverageType());
        summary.put("expectedStatus", generatedCase.expectedStatus());
        summary.put("statusClass", generatedCase.expectedStatus() / 100 + "xx");
        summary.put("schemaDigest", endpoint.schemaDigest());
        summary.put("assertions", generatedCase.assertions());
        summary.put("rationale", safeSourceText(generatedCase.rationale(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        summary.put("modelOutputValidated", true);
        summary.put("aggregateOnly", true);
        return summary;
    }

    private Map<String, Object> modelRequestTemplate(
            ApiAutomationEndpointSnapshot endpoint,
            ApiAutomationModelOutputParser.ModelGeneratedApiCase generatedCase
    ) {
        Map<String, Object> template = new LinkedHashMap<>(generatedCase.requestTemplate());
        template.put("httpMethod", endpoint.httpMethod());
        template.put("path", endpoint.path());
        template.put("parameterCount", endpoint.parameterCount());
        template.put("requestBodyPresent", endpoint.requestBodyPresent());
        template.put("bodyTemplateStored", false);
        template.put("secretValuesStored", false);
        template.put("endpointSnapshotId", endpoint.id().toString());
        template.put("modelOutputValidated", true);
        template.put("aggregateOnly", true);
        return template;
    }

    private String modelGenerationPayload(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> targets,
            GenerationRequest request,
            List<GenerationSourceTestCase> sourceTestCases
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaMarker", WP6_MODEL_SCHEMA_MARKER);
        payload.put("schemaVersion", ApiAutomationModelOutputParser.SCHEMA_VERSION);
        payload.put("projectId", request.projectId());
        payload.put("specId", spec.id().toString());
        payload.put("coverageTypes", request.coverageTypes());
        payload.put("caseCountPerApi", request.caseCountPerApi());
        payload.put("endpoints", targets.stream().map(this::endpointModelPayload).toList());
        payload.put("sourceTestCases", sourceTestCases.stream().map(GenerationSourceTestCase::summary).toList());
        payload.put("persistencePolicy", Map.of(
                "rawPromptStored", false,
                "rawModelResponseStored", false,
                "requestResponseBodyStored", false,
                "secretValuesStored", false,
                "aggregateOnly", true
        ));
        return writeJson(payload);
    }

    private Map<String, Object> endpointModelPayload(ApiAutomationEndpointSnapshot endpoint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("endpointSnapshotId", endpoint.id().toString());
        item.put("assetApiId", endpoint.assetApiId() == null ? null : endpoint.assetApiId().toString());
        item.put("method", endpoint.httpMethod());
        item.put("path", endpoint.path());
        item.put("summary", safeSourceText(endpoint.summary(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        item.put("operationId", safeSourceText(endpoint.operationId(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        item.put("tags", safeSourceText(endpoint.tags(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        item.put("parameterCount", endpoint.parameterCount());
        item.put("requestBodyPresent", endpoint.requestBodyPresent());
        item.put("responseStatuses", endpoint.responseStatuses());
        item.put("schemaDigest", endpoint.schemaDigest());
        item.put("aggregateOnly", true);
        return item;
    }

    private String modelFallbackReason(RuntimeException exception) {
        Throwable root = exception instanceof ApiAutomationModelGenerationException generationException
                && generationException.getCause() != null
                ? generationException.getCause()
                : exception;
        if (root instanceof BusinessException businessException) {
            return boundedText(businessException.getErrorCode().name() + ": " + businessException.getMessage(),
                    ERROR_SUMMARY_MAX_CHARS);
        }
        return boundedText("MODEL_GENERATION_FAILED: " + (StringUtils.hasText(root.getMessage())
                ? root.getMessage()
                : root.getClass().getSimpleName()), ERROR_SUMMARY_MAX_CHARS);
    }

    private RuntimeException unwrapModelGenerationException(ApiAutomationModelGenerationException exception) {
        return exception.getCause() instanceof RuntimeException runtimeException
                ? runtimeException
                : exception;
    }

    private List<ApiAutomationCase> fallbackCases(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> targets,
            GenerationRequest request,
            List<GenerationSourceTestCase> sourceTestCases
    ) {
        List<ApiAutomationCase> cases = new ArrayList<>();
        Map<UUID, UUID> sourceCaseIdByAssetApiId = sourceTestCases.stream()
                .filter(sourceCase -> sourceCase.apiId() != null)
                .collect(Collectors.toMap(
                        GenerationSourceTestCase::apiId,
                        GenerationSourceTestCase::id,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        for (ApiAutomationEndpointSnapshot endpoint : targets) {
            UUID sourceTestCaseId = endpoint.assetApiId() == null ? null : sourceCaseIdByAssetApiId.get(endpoint.assetApiId());
            request.coverageTypes().stream()
                    .limit(request.caseCountPerApi())
                    .map(coverageType -> fallbackCase(spec, endpoint, coverageType, sourceTestCaseId))
                    .forEach(cases::add);
        }
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "未生成任何接口自动化用例草稿");
        }
        return cases;
    }

    private ApiAutomationCase fallbackCase(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint,
            String coverageType,
            UUID sourceTestCaseId
    ) {
        int expectedStatus = expectedStatus(endpoint, coverageType);
        return new ApiAutomationCase(
                UUID.randomUUID(),
                null,
                spec.projectId(),
                spec.id(),
                endpoint.id(),
                endpoint.assetApiId(),
                sourceTestCaseId,
                boundedText("[" + coverageType + "] " + endpoint.httpMethod() + " " + endpoint.path(), 256),
                endpoint.httpMethod(),
                endpoint.path(),
                coverageType,
                expectedStatus,
                writeJson(assertionSummary(endpoint, coverageType, expectedStatus)),
                writeJson(requestTemplate(endpoint)),
                "FALLBACK",
                "DRAFT",
                null,
                null
        );
    }

    private GenerationSourceTestCase sourceTestCaseSummary(TestCaseResponse testCase) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("assetTestCaseId", testCase.id().toString());
        summary.put("assetApiId", testCase.apiId() == null ? null : testCase.apiId().toString());
        summary.put("code", safeSourceText(testCase.code(), 64));
        summary.put("title", safeSourceText(testCase.title(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        summary.put("status", nullToEmpty(testCase.status()));
        summary.put("priority", nullToEmpty(testCase.priority()));
        summary.put("tags", safeSourceText(testCase.tags(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        summary.put("source", nullToEmpty(testCase.source()));
        summary.put("sourceRefDigest", StringUtils.hasText(testCase.sourceRef()) ? sha256(testCase.sourceRef()) : null);
        summary.put("stepCount", testCase.steps() == null ? 0 : testCase.steps().size());
        summary.put("steps", sourceStepSummaries(testCase.steps()));
        summary.put("rawCandidateStored", false);
        summary.put("reviewCommentStored", false);
        summary.put("aggregateOnly", true);
        return new GenerationSourceTestCase(testCase.id(), testCase.apiId(), summary);
    }

    private List<Map<String, Object>> sourceStepSummaries(List<TestCaseStepResponse> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .limit(GENERATION_SOURCE_STEP_MAX)
                .map(step -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("stepOrder", step.stepOrder());
                    summary.put("actionSummary", safeSourceText(step.action(), GENERATION_SOURCE_TEXT_MAX_CHARS));
                    summary.put("expectedSummary", safeSourceText(step.expectedResult(), GENERATION_SOURCE_TEXT_MAX_CHARS));
                    return summary;
                })
                .toList();
    }

    private ApiAutomationCase caseWithTask(ApiAutomationCase automationCase, UUID taskId, Instant now) {
        return new ApiAutomationCase(
                automationCase.id(),
                taskId,
                automationCase.projectId(),
                automationCase.specId(),
                automationCase.endpointSnapshotId(),
                automationCase.assetApiId(),
                automationCase.assetTestCaseId(),
                automationCase.title(),
                automationCase.httpMethod(),
                automationCase.path(),
                automationCase.coverageType(),
                automationCase.expectedStatus(),
                automationCase.assertionSummaryJson(),
                automationCase.requestTemplateJson(),
                automationCase.source(),
                automationCase.status(),
                now,
                now
        );
    }

    private Map<String, Object> assertionSummary(
            ApiAutomationEndpointSnapshot endpoint,
            String coverageType,
            int expectedStatus
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("coverageType", coverageType);
        summary.put("expectedStatus", expectedStatus);
        summary.put("statusClass", expectedStatus / 100 + "xx");
        summary.put("schemaDigest", endpoint.schemaDigest());
        summary.put("assertions", List.of("STATUS_CODE", "RESPONSE_TIME_BOUNDED", "SCHEMA_DIGEST_PRESENT"));
        summary.put("aggregateOnly", true);
        return summary;
    }

    private Map<String, Object> requestTemplate(ApiAutomationEndpointSnapshot endpoint) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("httpMethod", endpoint.httpMethod());
        template.put("path", endpoint.path());
        template.put("parameterCount", endpoint.parameterCount());
        template.put("requestBodyPresent", endpoint.requestBodyPresent());
        template.put("bodyTemplateStored", false);
        template.put("secretValuesStored", false);
        template.put("endpointSnapshotId", endpoint.id().toString());
        template.put("aggregateOnly", true);
        return template;
    }

    private Map<String, Object> inputSummary(
            ApiAutomationSpec spec,
            GenerationRequest request,
            List<ApiAutomationEndpointSnapshot> targets,
            List<GenerationSourceTestCase> sourceTestCases,
            GenerationAttempt attempt
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("specId", spec.id().toString());
        summary.put("apiCount", targets.size());
        summary.put("caseCount", attempt.cases().size());
        summary.put("coverageTypes", request.coverageTypes());
        summary.put("assetApiIds", targets.stream().map(ApiAutomationEndpointSnapshot::assetApiId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .toList());
        summary.put("assetTestCaseIds", uuidStrings(request.assetTestCaseIds()));
        summary.put("sourceTestCaseCount", sourceTestCases.size());
        // 只保存 WP3 已发布测试用例的摘要证据，不回读或持久化 WP5 候选正文、评审评论和 sourceRef 明文。
        summary.put("sourceTestCases", sourceTestCases.stream()
                .map(GenerationSourceTestCase::summary)
                .toList());
        summary.put("generationMode", request.generationMode());
        summary.put("modelRequested", "MODEL_WITH_FALLBACK".equals(request.generationMode()));
        summary.put("modelGenerationReady", true);
        summary.put("modelOutputValidated", attempt.modelOutputValidated());
        summary.put("modelInvocationId", attempt.modelInvocationId());
        summary.put("promptVersion", attempt.promptVersion());
        summary.put("modelProviderName", attempt.modelProviderName());
        summary.put("modelName", attempt.modelName());
        summary.put("modelProviderFallbackUsed", attempt.modelProviderFallbackUsed());
        summary.put("fallbackUsed", attempt.fallbackUsed());
        summary.put("fallbackReason", attempt.fallbackReason());
        summary.put("rawRequestResponseStored", false);
        summary.put("rawModelResponseStored", false);
        summary.put("aggregateOnly", true);
        return summary;
    }

    private int expectedStatus(ApiAutomationEndpointSnapshot endpoint, String coverageType) {
        List<Integer> statuses = responseStatusCodes(endpoint.responseStatuses());
        if ("EXCEPTION".equals(coverageType)) {
            return statuses.stream()
                    .filter(status -> status >= 400 && status < 500)
                    .findFirst()
                    .orElse(400);
        }
        return statuses.stream()
                .filter(status -> status >= 200 && status < 300)
                .findFirst()
                .orElse(statuses.isEmpty() ? 200 : statuses.getFirst());
    }

    private List<Integer> responseStatusCodes(String responseStatuses) {
        if (!StringUtils.hasText(responseStatuses)) {
            return List.of();
        }
        return List.of(responseStatuses.split(",")).stream()
                .map(String::trim)
                .filter(value -> value.matches("\\d{3}"))
                .map(Integer::parseInt)
                .toList();
    }

    private List<String> normalizeCoverageTypes(List<String> coverageTypes) {
        List<String> normalized = coverageTypes == null ? List.of() : coverageTypes.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of("SMOKE");
        }
        normalized.forEach(value -> {
            if (!COVERAGE_TYPES.contains(value)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "coverageTypes 不合法: " + value);
            }
        });
        return normalized;
    }

    private String normalizeGenerationMode(String generationMode) {
        String normalized = StringUtils.hasText(generationMode)
                ? generationMode.trim().toUpperCase(Locale.ROOT)
                : "FALLBACK_ONLY";
        if (!GENERATION_MODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "generationMode 不合法: " + generationMode);
        }
        return normalized;
    }

    private int normalizeCaseCountPerApi(Integer caseCountPerApi, int coverageTypeCount) {
        int normalized = caseCountPerApi == null ? Math.min(coverageTypeCount, 3) : caseCountPerApi;
        if (normalized < 1 || normalized > GENERATION_CASE_COUNT_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "caseCountPerApi 必须在 1-" + GENERATION_CASE_COUNT_MAX + " 之间");
        }
        return normalized;
    }

    private List<UUID> normalizedUuidList(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> uuidStrings(List<UUID> values) {
        return values.stream().map(UUID::toString).toList();
    }

    private ApiAutomationSpecDetailResponse parseAndPersist(ApiAutomationSpec spec, String content, String actor) {
        try {
            OpenApiParseResult parseResult = parser.parse(content, properties.effectiveEndpointMaxCount());
            Instant now = Instant.now();
            ApiAutomationSpec parsedSpec = new ApiAutomationSpec(
                    spec.id(),
                    spec.projectId(),
                    spec.sourceType(),
                    spec.sourceRef(),
                    spec.name(),
                    spec.versionLabel(),
                    spec.specDigest(),
                    spec.contentSizeBytes(),
                    parseResult.sanitizedSpecJson(),
                    writeJson(parseResult.summary()),
                    "PARSED",
                    OpenApiSpecParser.PARSER_VERSION,
                    parseResult.endpoints().size(),
                    null,
                    spec.createdBy(),
                    actor,
                    now,
                    spec.createdAt(),
                    now
            );
            repository.updateSpecParseResult(parsedSpec);
            repository.deleteEndpointSnapshots(spec.id());
            List<ApiAutomationEndpointSnapshot> snapshots = snapshots(parsedSpec, parseResult.endpoints(), now);
            snapshots.forEach(repository::insertEndpointSnapshot);
            audit("api_automation.spec.parsed", parsedSpec, "SUCCESS", Map.of(
                    "endpointCount", parsedSpec.endpointCount(),
                    "parserVersion", parsedSpec.parserVersion()
            ));
            return toDetail(parsedSpec, snapshots);
        } catch (BusinessException exception) {
            ApiAutomationSpec failedSpec = markParseFailed(spec, exception.getMessage(), actor);
            repository.updateSpecParseResult(failedSpec);
            audit("api_automation.spec.parse_failed", failedSpec, "FAILED", Map.of(
                    "errorCode", exception.getErrorCode().name(),
                    "errorSummary", failedSpec.parseErrorSummary()
            ));
            throw exception;
        }
    }

    private List<ApiAutomationEndpointSnapshot> snapshots(
            ApiAutomationSpec spec,
            List<ParsedOpenApiEndpoint> endpoints,
            Instant now
    ) {
        return endpoints.stream()
                .map(endpoint -> new ApiAutomationEndpointSnapshot(
                        UUID.randomUUID(),
                        spec.id(),
                        spec.projectId(),
                        endpoint.serviceName(),
                        endpoint.operationId(),
                        endpoint.httpMethod(),
                        endpoint.path(),
                        endpoint.summary(),
                        String.join(",", endpoint.tags()),
                        endpoint.parameterCount(),
                        endpoint.requestBodyPresent(),
                        String.join(",", endpoint.responseStatuses()),
                        endpoint.schemaDigest(),
                        "UNKNOWN",
                        null,
                        "{}",
                        null,
                        null,
                        null,
                        now,
                        now
                ))
                .toList();
    }

    private ApiAutomationSpec markParsing(ApiAutomationSpec spec, Instant now, String actor) {
        return new ApiAutomationSpec(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                spec.sanitizedSpecJson(),
                spec.parseSummaryJson(),
                "PARSING",
                OpenApiSpecParser.PARSER_VERSION,
                spec.endpointCount(),
                null,
                spec.createdBy(),
                actor,
                spec.parsedAt(),
                spec.createdAt(),
                now
        );
    }

    private ApiAutomationSpec markParseFailed(ApiAutomationSpec spec, String message, String actor) {
        Instant now = Instant.now();
        return new ApiAutomationSpec(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                "{}",
                writeJson(Map.of(
                        "parserVersion", OpenApiSpecParser.PARSER_VERSION,
                        "endpointCount", 0,
                        "parseFailed", true,
                        "aggregateOnly", true
                )),
                "PARSE_FAILED",
                OpenApiSpecParser.PARSER_VERSION,
                0,
                boundedText(message, ERROR_SUMMARY_MAX_CHARS),
                spec.createdBy(),
                actor,
                null,
                spec.createdAt(),
                now
        );
    }

    private List<ApiAutomationEndpointSnapshot> evaluateAndPersistDiff(ApiAutomationSpec spec) {
        Instant now = Instant.now();
        // WP3 API 当前没有 serviceName 字段，M3 先按项目内 method + path 匹配，serviceName 仅作为展示和后续扩展依据。
        Map<String, List<ApiResponseDTO>> assetApisByKey = assetApisByKey(spec.projectId());
        List<ApiAutomationEndpointSnapshot> updated = repository.endpointSnapshots(spec.id()).stream()
                .map(endpoint -> diffEndpoint(endpoint, assetApisByKey, now))
                .toList();
        updated.forEach(repository::updateEndpointSnapshotDiff);
        return updated;
    }

    private ApiAutomationEndpointSnapshot diffEndpoint(
            ApiAutomationEndpointSnapshot endpoint,
            Map<String, List<ApiResponseDTO>> assetApisByKey,
            Instant now
    ) {
        if (endpoint.path().length() > WP3_API_PATH_MAX_CHARS) {
            return endpointWithDiff(endpoint, null, "SKIPPED", Map.of(
                    "reason", "PATH_TOO_LONG",
                    "action", "NONE",
                    "maxPathChars", WP3_API_PATH_MAX_CHARS
            ), now, endpoint.syncedAt(), null);
        }
        List<ApiResponseDTO> matches = assetApisByKey.getOrDefault(assetKey(endpoint.httpMethod(), endpoint.path()), List.of());
        if (matches.isEmpty()) {
            return endpointWithDiff(endpoint, null, "NEW", Map.of(
                    "reason", "NO_MATCHING_WP3_API",
                    "action", "CREATE",
                    "sourceRef", endpointSourceRef(endpoint)
            ), now, endpoint.syncedAt(), null);
        }
        if (matches.size() > 1) {
            return endpointWithDiff(endpoint, null, "CONFLICT", Map.of(
                    "reason", "DUPLICATE_WP3_API_MATCHES",
                    "action", "REVIEW",
                    "assetApiIds", matches.stream().map(value -> value.id().toString()).toList()
            ), now, endpoint.syncedAt(), null);
        }
        ApiResponseDTO asset = matches.getFirst();
        boolean sameDigest = endpoint.schemaDigest().equals(assetSchemaDigest(asset));
        return endpointWithDiff(endpoint, asset.id(), sameDigest ? "MATCHED" : "CHANGED", Map.of(
                "reason", sameDigest ? "SCHEMA_DIGEST_MATCHED" : "SCHEMA_DIGEST_CHANGED",
                "action", sameDigest ? "NONE" : "UPDATE",
                "assetApiId", asset.id().toString(),
                "assetSource", nullToEmpty(asset.source()),
                "assetSourceRef", nullToEmpty(asset.sourceRef())
        ), now, endpoint.syncedAt(), null);
    }

    private SyncAttempt syncEndpoint(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint,
            SyncApiAutomationSpecCommand command,
            Instant now
    ) {
        // 只有 NEW 和显式允许的 CHANGED 写入 WP3；冲突、跳过和已匹配 endpoint 保留人工运营判断。
        if ("NEW".equals(endpoint.diffStatus())) {
            return createAssetApi(spec, endpoint, now);
        }
        if ("CHANGED".equals(endpoint.diffStatus()) && command.shouldIncludeChanged()) {
            return updateAssetApi(spec, endpoint, now);
        }
        String result = "MATCHED".equals(endpoint.diffStatus()) ? "MATCHED" : "SKIPPED";
        String message = "CHANGED".equals(endpoint.diffStatus()) ? "includeChanged=false" : "diffStatus=" + endpoint.diffStatus();
        return new SyncAttempt(endpoint, syncItem(endpoint, result, message));
    }

    private SyncAttempt createAssetApi(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, Instant now) {
        try {
            ApiResponseDTO asset = assetApiService.createOpenApiSyncedApi(syncRequest(spec, endpoint, true));
            ApiAutomationEndpointSnapshot synced = endpointWithDiff(endpoint, asset.id(), "MATCHED", Map.of(
                    "reason", "SYNC_CREATED",
                    "action", "CREATE",
                    "assetApiId", asset.id().toString()
            ), endpoint.lastDiffAt(), now, null);
            repository.updateEndpointSnapshotDiff(synced);
            return new SyncAttempt(synced, syncItem(synced, endpoint.diffStatus(), "CREATED", "已创建 WP3 API 资产"));
        } catch (BusinessException exception) {
            ApiAutomationEndpointSnapshot failed = endpointWithDiff(endpoint, endpoint.assetApiId(), endpoint.diffStatus(),
                    readSummary(endpoint.diffSummaryJson()), endpoint.lastDiffAt(), endpoint.syncedAt(),
                    boundedText(exception.getMessage(), ERROR_SUMMARY_MAX_CHARS));
            repository.updateEndpointSnapshotDiff(failed);
            return new SyncAttempt(failed, syncItem(failed, "FAILED", failed.syncErrorSummary()));
        }
    }

    private SyncAttempt updateAssetApi(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, Instant now) {
        if (endpoint.assetApiId() == null) {
            ApiAutomationEndpointSnapshot skipped = endpointWithDiff(endpoint, null, "CONFLICT", Map.of(
                    "reason", "MISSING_ASSET_API_ID",
                    "action", "REVIEW"
            ), endpoint.lastDiffAt(), endpoint.syncedAt(), "缺少可更新的 WP3 API 资产 ID");
            repository.updateEndpointSnapshotDiff(skipped);
            return new SyncAttempt(skipped, syncItem(skipped, "FAILED", skipped.syncErrorSummary()));
        }
        try {
            ApiResponseDTO asset = assetApiService.updateOpenApiSyncedApi(endpoint.assetApiId(), syncRequest(spec, endpoint, false));
            ApiAutomationEndpointSnapshot synced = endpointWithDiff(endpoint, asset.id(), "MATCHED", Map.of(
                    "reason", "SYNC_UPDATED",
                    "action", "UPDATE",
                    "assetApiId", asset.id().toString()
            ), endpoint.lastDiffAt(), now, null);
            repository.updateEndpointSnapshotDiff(synced);
            return new SyncAttempt(synced, syncItem(synced, endpoint.diffStatus(), "UPDATED", "已更新 WP3 API 资产"));
        } catch (BusinessException exception) {
            ApiAutomationEndpointSnapshot failed = endpointWithDiff(endpoint, endpoint.assetApiId(), endpoint.diffStatus(),
                    readSummary(endpoint.diffSummaryJson()), endpoint.lastDiffAt(), endpoint.syncedAt(),
                    boundedText(exception.getMessage(), ERROR_SUMMARY_MAX_CHARS));
            repository.updateEndpointSnapshotDiff(failed);
            return new SyncAttempt(failed, syncItem(failed, "FAILED", failed.syncErrorSummary()));
        }
    }

    private SyncOpenApiRequest syncRequest(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, boolean create) {
        return new SyncOpenApiRequest(
                boundedText(StringUtils.hasText(endpoint.summary()) ? endpoint.summary() : endpoint.httpMethod() + " " + endpoint.path(),
                        WP3_API_SUMMARY_MAX_CHARS),
                boundedText("WP6 OpenAPI sync " + nullToEmpty(endpoint.serviceName()) + " " + nullToEmpty(endpoint.operationId()), 512),
                endpoint.httpMethod(),
                endpoint.path(),
                boundedText(endpoint.schemaDigest(), WP3_API_VERSION_MAX_CHARS),
                requestSchema(endpoint),
                responseSchema(endpoint),
                spec.projectId(),
                endpointSourceRef(endpoint),
                create ? "ACTIVE" : null
        );
    }

    private String requestSchema(ApiAutomationEndpointSnapshot endpoint) {
        return writeJson(Map.of(
                "wp6SchemaDigest", endpoint.schemaDigest(),
                "parameterCount", endpoint.parameterCount(),
                "requestBodyPresent", endpoint.requestBodyPresent(),
                "aggregateOnly", true
        ));
    }

    private String responseSchema(ApiAutomationEndpointSnapshot endpoint) {
        return writeJson(Map.of(
                "wp6SchemaDigest", endpoint.schemaDigest(),
                "responseStatuses", StringUtils.hasText(endpoint.responseStatuses()) ? endpoint.responseStatuses() : "",
                "aggregateOnly", true
        ));
    }

    private ApiAutomationEndpointSnapshot endpointWithDiff(
            ApiAutomationEndpointSnapshot endpoint,
            UUID assetApiId,
            String diffStatus,
            Map<String, Object> diffSummary,
            Instant lastDiffAt,
            Instant syncedAt,
            String syncErrorSummary
    ) {
        return new ApiAutomationEndpointSnapshot(
                endpoint.id(),
                endpoint.specId(),
                endpoint.projectId(),
                endpoint.serviceName(),
                endpoint.operationId(),
                endpoint.httpMethod(),
                endpoint.path(),
                endpoint.summary(),
                endpoint.tags(),
                endpoint.parameterCount(),
                endpoint.requestBodyPresent(),
                endpoint.responseStatuses(),
                endpoint.schemaDigest(),
                diffStatus,
                assetApiId,
                writeJson(diffSummary == null ? Map.of() : diffSummary),
                lastDiffAt,
                syncedAt,
                syncErrorSummary,
                endpoint.createdAt(),
                Instant.now()
        );
    }

    private Map<String, List<ApiResponseDTO>> assetApisByKey(String projectId) {
        List<ApiResponseDTO> assets = new ArrayList<>();
        // 防御性分页上限避免一次 diff 因异常资产量拖垮控制面，后续可按 WP3 查询能力改为精确按 path 拉取。
        for (int index = 0; index < WP3_API_PAGE_LIMIT; index++) {
            AssetListRequest request = new AssetListRequest();
            request.setProjectId(projectId);
            request.setLifecycleStatus("ACTIVE");
            request.setIndex(index);
            request.setSize(WP3_API_PAGE_SIZE);
            PageResponse<ApiResponseDTO> page = assetApiService.listApis(request);
            assets.addAll(page.items());
            if (page.items().size() < WP3_API_PAGE_SIZE) {
                break;
            }
        }
        return assets.stream().collect(Collectors.groupingBy(
                asset -> assetKey(asset.httpMethod(), asset.path()),
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private Map<String, Integer> countDiffStatuses(List<ApiAutomationEndpointSnapshot> endpoints) {
        Map<String, Integer> counts = initializedCounts(DIFF_STATUSES);
        endpoints.forEach(endpoint -> counts.computeIfPresent(endpoint.diffStatus(), (key, value) -> value + 1));
        return counts;
    }

    private Map<String, Integer> countSyncResults(List<ApiAutomationSyncItemResponse> items) {
        Map<String, Integer> counts = initializedCounts(List.of("CREATED", "UPDATED", "MATCHED", "SKIPPED", "FAILED"));
        items.forEach(item -> counts.computeIfPresent(item.result(), (key, value) -> value + 1));
        return counts;
    }

    private Map<String, Integer> initializedCounts(List<String> keys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        keys.forEach(key -> counts.put(key, 0));
        return counts;
    }

    private String syncResult(Map<String, Integer> counts) {
        return counts.getOrDefault("FAILED", 0) > 0 ? "FAILED" : "SUCCESS";
    }

    private ApiAutomationSyncItemResponse syncItem(ApiAutomationEndpointSnapshot endpoint, String result, String message) {
        return syncItem(endpoint, endpoint.diffStatus(), result, message);
    }

    private ApiAutomationSyncItemResponse syncItem(
            ApiAutomationEndpointSnapshot endpoint,
            String beforeStatus,
            String result,
            String message
    ) {
        return new ApiAutomationSyncItemResponse(
                endpoint.id(),
                endpoint.assetApiId(),
                endpoint.httpMethod(),
                endpoint.path(),
                beforeStatus,
                result,
                message
        );
    }

    private ApiAutomationSpecDetailResponse toDetail(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> endpoints
    ) {
        return new ApiAutomationSpecDetailResponse(
                toSpecResponse(spec),
                readSummary(spec.parseSummaryJson()),
                endpoints.stream().map(this::toEndpointResponse).toList()
        );
    }

    private ApiAutomationGenerationTaskDetailResponse toGenerationTaskDetail(
            ApiAutomationGenerationTask task,
            List<ApiAutomationCase> cases,
            List<ApiAutomationScriptBundle> scriptBundles
    ) {
        return new ApiAutomationGenerationTaskDetailResponse(
                new ApiAutomationGenerationTaskResponse(
                        task.id(),
                        task.projectId(),
                        task.specId(),
                        task.requestKey(),
                        task.requestDigest(),
                        task.generationMode(),
                        readStringList(task.coverageTypesJson()),
                        task.status(),
                        task.promptKey(),
                        task.promptVersion(),
                        task.modelInvocationId(),
                        task.fallbackUsed(),
                        task.apiCount(),
                        task.caseCount(),
                        readSummary(task.inputSummaryJson()),
                        task.errorSummary(),
                        task.createdAt(),
                        task.updatedAt()
                ),
                cases.stream().map(this::toAutomationCaseResponse).toList(),
                scriptBundles.stream().map(this::toScriptBundleResponse).toList()
        );
    }

    private ApiAutomationCaseResponse toAutomationCaseResponse(ApiAutomationCase automationCase) {
        return new ApiAutomationCaseResponse(
                automationCase.id(),
                automationCase.endpointSnapshotId(),
                automationCase.assetApiId(),
                automationCase.assetTestCaseId(),
                automationCase.title(),
                automationCase.httpMethod(),
                automationCase.path(),
                automationCase.coverageType(),
                automationCase.expectedStatus(),
                readSummary(automationCase.assertionSummaryJson()),
                readSummary(automationCase.requestTemplateJson()),
                automationCase.source(),
                automationCase.status(),
                automationCase.createdAt(),
                automationCase.updatedAt()
        );
    }

    private ApiAutomationScriptBundleResponse toScriptBundleResponse(ApiAutomationScriptBundle bundle) {
        return new ApiAutomationScriptBundleResponse(
                bundle.id(),
                bundle.projectId(),
                bundle.taskId(),
                bundle.status(),
                bundle.bundleDigest(),
                bundle.fileCount(),
                readSummary(bundle.fileTreeSummaryJson()),
                readSummary(bundle.dependencySummaryJson()),
                bundle.staticCheckStatus(),
                readSummary(bundle.staticCheckSummaryJson()),
                bundle.reviewNote(),
                bundle.submittedBy(),
                bundle.approvedBy(),
                bundle.submittedAt(),
                bundle.approvedAt(),
                bundle.rejectedAt(),
                bundle.createdAt(),
                bundle.updatedAt()
        );
    }

    private ApiAutomationRunDetailResponse toRunDetail(ApiAutomationRun run, List<ApiAutomationRunResult> results) {
        return new ApiAutomationRunDetailResponse(
                toRunResponse(run),
                results.stream().map(this::toRunResultResponse).toList()
        );
    }

    private ApiAutomationRunResponse toRunResponse(ApiAutomationRun run) {
        return new ApiAutomationRunResponse(
                run.id(),
                run.projectId(),
                run.bundleId(),
                run.environmentId(),
                run.baseUrlDigest(),
                run.baseUrlHost(),
                run.status(),
                run.timeoutSeconds(),
                run.caseCount(),
                run.traceId(),
                run.runnerMode(),
                run.errorCode(),
                run.errorSummary(),
                run.startedAt(),
                run.completedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    private ApiAutomationRunResultResponse toRunResultResponse(ApiAutomationRunResult result) {
        return new ApiAutomationRunResultResponse(
                result.id(),
                result.runId(),
                result.caseId(),
                result.status(),
                result.durationMs(),
                readSummary(result.assertionSummaryJson()),
                result.errorCode(),
                result.errorSummary(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    private Map<String, Integer> resultCounts(List<ApiAutomationRunResult> results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        results.forEach(result -> counts.merge(result.status(), 1, Integer::sum));
        return counts;
    }

    private Map<String, Object> runExportRedactionPolicy() {
        return Map.of(
                "rawBaseUrlExported", false,
                "baseUrlDigestExported", true,
                "baseUrlHostExported", true,
                "rawRequestResponseExported", false,
                "stdoutStderrExported", false,
                "secretValuesExported", false,
                "assertionSummaryAggregateOnly", true
        );
    }

    private ApiAutomationSpecResponse toSpecResponse(ApiAutomationSpec spec) {
        return new ApiAutomationSpecResponse(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                spec.status(),
                spec.parserVersion(),
                spec.endpointCount(),
                spec.parseErrorSummary(),
                spec.parsedAt(),
                spec.createdAt(),
                spec.updatedAt()
        );
    }

    private ApiAutomationEndpointSnapshotResponse toEndpointResponse(ApiAutomationEndpointSnapshot snapshot) {
        return new ApiAutomationEndpointSnapshotResponse(
                snapshot.id(),
                snapshot.serviceName(),
                snapshot.operationId(),
                snapshot.httpMethod(),
                snapshot.path(),
                snapshot.summary(),
                snapshot.tags(),
                snapshot.parameterCount(),
                snapshot.requestBodyPresent(),
                snapshot.responseStatuses(),
                snapshot.schemaDigest(),
                snapshot.diffStatus(),
                snapshot.assetApiId(),
                readSummary(snapshot.diffSummaryJson()),
                snapshot.lastDiffAt(),
                snapshot.syncedAt(),
                snapshot.syncErrorSummary()
        );
    }

    private ApiAutomationSpec requireParsedSpec(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        if (!"PARSED".equals(spec.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "OpenAPI 规格未完成解析，不能执行 diff/sync");
        }
        return spec;
    }

    private ApiAutomationScriptBundle requireScriptBundle(UUID id) {
        return repository.scriptBundle(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "脚本包不存在: " + id));
    }

    private ApiAutomationSpec requireSpec(UUID id) {
        return repository.spec(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI 规格不存在: " + id));
    }

    private ApiAutomationScriptBundle scriptBundleWithReview(
            ApiAutomationScriptBundle bundle,
            String status,
            String reviewNote,
            String submittedBy,
            String approvedBy,
            Instant submittedAt,
            Instant approvedAt,
            Instant rejectedAt,
            String updatedBy,
            Instant updatedAt
    ) {
        return new ApiAutomationScriptBundle(
                bundle.id(),
                bundle.projectId(),
                bundle.taskId(),
                status,
                bundle.bundleDigest(),
                bundle.fileCount(),
                bundle.fileTreeSummaryJson(),
                bundle.dependencySummaryJson(),
                bundle.staticCheckStatus(),
                bundle.staticCheckSummaryJson(),
                reviewNote,
                submittedBy,
                approvedBy,
                submittedAt,
                approvedAt,
                rejectedAt,
                bundle.createdBy(),
                updatedBy,
                bundle.createdAt(),
                updatedAt
        );
    }

    private String reviewNoteOrCurrent(
            ApiAutomationScriptBundle bundle,
            ReviewApiAutomationScriptBundleCommand command
    ) {
        String note = boundedNullableText(command == null ? null : command.note(), 512);
        return StringUtils.hasText(note) ? note : bundle.reviewNote();
    }

    private String assetKey(String httpMethod, String path) {
        return (httpMethod == null ? "" : httpMethod.trim().toUpperCase(Locale.ROOT)) + " " + nullToEmpty(path).trim();
    }

    private String assetSchemaDigest(ApiResponseDTO asset) {
        String requestDigest = schemaDigest(asset.requestSchema());
        return StringUtils.hasText(requestDigest) ? requestDigest : schemaDigest(asset.responseSchema());
    }

    private String schemaDigest(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return "";
        }
        try {
            Map<String, Object> value = objectMapper.readValue(schemaJson, MAP_TYPE);
            Object digest = value.get("wp6SchemaDigest");
            if (digest == null) {
                digest = value.get("schemaDigest");
            }
            return digest == null ? "" : digest.toString();
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private String endpointSourceRef(ApiAutomationEndpointSnapshot endpoint) {
        return "wp6:" + endpoint.specId() + ":" + endpoint.id();
    }

    private Map<String, Object> readSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("parseSummaryUnreadable", true, "aggregateOnly", true);
        }
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAPI 摘要序列化失败");
        }
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceType 仅支持 TEXT/UPLOAD/URL");
        }
        return normalized;
    }

    private ApiAutomationSpecQuery canonicalProjectQuery(ApiAutomationSpecQuery query) {
        if (!StringUtils.hasText(query.projectId())) {
            return query;
        }
        // 权限解析允许项目 ID 或编码，数据库过滤必须统一使用平台上下文返回的规范资源 ID。
        String projectId = contextClient.projectContext(query.projectId()).resourceId();
        return new ApiAutomationSpecQuery(projectId, query.status(), query.keyword(), query.limit(), query.offset());
    }

    private String sanitizeSourceRef(String sourceRef) {
        if (!StringUtils.hasText(sourceRef)) {
            return null;
        }
        String trimmed = sourceRef.trim();
        int queryIndex = trimmed.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        return boundedText(withoutQuery, 512);
    }

    private String boundedNullableText(String value, int maxLength) {
        return StringUtils.hasText(value) ? boundedText(value, maxLength) : null;
    }

    private String boundedText(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String safeSourceText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return boundedText(redactSensitiveText(value), maxLength);
    }

    private String redactSensitiveText(String value) {
        String redacted = value;
        for (Pattern pattern : SENSITIVE_TEXT_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("[REDACTED]");
        }
        return redacted;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }

    private void audit(
            String action,
            ApiAutomationSpec spec,
            String result,
            Map<String, Object> afterJson
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("status", spec.status());
        payload.put("specId", spec.id().toString());
        contextClient.writeAuditEvent(
                action,
                "API_AUTOMATION_SPEC",
                spec.id().toString(),
                spec.projectId(),
                result,
                payload
        );
    }

    private void auditGenerationTask(
            ApiAutomationGenerationTask task,
            String result,
            Map<String, Object> afterJson
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("taskId", task.id().toString());
        payload.put("specId", task.specId().toString());
        payload.put("status", task.status());
        contextClient.writeAuditEvent(
                "api_automation.generation.created",
                "API_AUTOMATION_GENERATION_TASK",
                task.id().toString(),
                task.projectId(),
                result,
                payload
        );
    }

    private void auditScriptBundle(
            ApiAutomationScriptBundle bundle,
            String result,
            String action,
            Map<String, Object> afterJson
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("bundleId", bundle.id().toString());
        payload.put("taskId", bundle.taskId().toString());
        payload.put("status", bundle.status());
        payload.put("staticCheckStatus", bundle.staticCheckStatus());
        payload.put("reviewAction", action);
        contextClient.writeAuditEvent(
                "GENERATED".equals(action) ? "api_automation.bundle.generated" : "api_automation.bundle.reviewed",
                "API_AUTOMATION_SCRIPT_BUNDLE",
                bundle.id().toString(),
                bundle.projectId(),
                result,
                payload
        );
    }

    private void auditRun(
            ApiAutomationRun run,
            String result,
            String action,
            Map<String, Object> afterJson
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("runId", run.id().toString());
        payload.put("bundleId", run.bundleId().toString());
        payload.put("status", run.status());
        payload.put("runnerMode", run.runnerMode());
        payload.put("runAction", action);
        contextClient.writeAuditEvent(
                runAuditActionName(action),
                "API_AUTOMATION_RUN",
                run.id().toString(),
                run.projectId(),
                result,
                payload
        );
    }

    private String runAuditActionName(String action) {
        return switch (action) {
            case "BLOCKED", "STARTED" -> "api_automation.run.started";
            case "CANCELED", "CANCEL_REJECTED", "CANCEL_SKIPPED" -> "api_automation.run.canceled";
            case "EXPORTED" -> "api_automation.exported";
            default -> "api_automation.run.completed";
        };
    }

    private record GenerationRequest(
            String projectId,
            UUID specId,
            String requestKey,
            String requestDigest,
            String generationMode,
            List<String> coverageTypes,
            List<UUID> assetApiIds,
            List<UUID> assetTestCaseIds,
            int caseCountPerApi
    ) {
    }

    private record GenerationSourceTestCase(
            UUID id,
            UUID apiId,
            Map<String, Object> summary
    ) {
    }

    private record GenerationAttempt(
            List<ApiAutomationCase> cases,
            boolean fallbackUsed,
            String fallbackReason,
            String modelInvocationId,
            String promptVersion,
            String modelProviderName,
            String modelName,
            boolean modelProviderFallbackUsed,
            boolean modelOutputValidated,
            String errorSummary
    ) {
    }

    private record SyncAttempt(
            ApiAutomationEndpointSnapshot snapshot,
            ApiAutomationSyncItemResponse response
    ) {
    }

    private record ScriptFile(
            String path,
            String kind,
            String content
    ) {
    }

    private record RunTarget(
            String normalizedBaseUrl,
            String host,
            String digest,
            boolean blocked,
            boolean allowed
    ) {
    }

    private record RunBlock(
            String errorCode,
            String errorSummary
    ) {
    }

    private record RunSecretRefs(
            int count,
            List<String> digests
    ) {
    }

    private record StaticCheckResult(
            String status,
            Map<String, Object> summary
    ) {
    }

    private static final class ApiAutomationModelGenerationException extends RuntimeException {

        private final ModelInvocationResult response;

        private ApiAutomationModelGenerationException(ModelInvocationResult response, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.response = response;
        }

        private ModelInvocationResult response() {
            return response;
        }
    }
}
