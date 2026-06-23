package com.songhg.veri.agent.apiautomation.application;

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
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationGenerationTaskPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationGenerationTaskQuery;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationHealthResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunExportResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationScriptBundleResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncItemResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncPreviewItemResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncPreviewResponse;
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
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ApiAutomationService {

    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "UPLOAD", "URL");
    private static final int ERROR_SUMMARY_MAX_CHARS = 512;
    private static final Set<String> SCRIPT_REVIEW_SUBMITTABLE_STATUSES = Set.of("DRAFT", "REJECTED");

    private final ApiAutomationRepository repository;
    private final ApiAutomationRunnerPort runnerPort;
    private final OpenApiSpecParser parser;
    private final ApiAutomationProperties properties;
    private final ApiAutomationPlatformContextClient contextClient;
    private final ApiAutomationActorResolver actorResolver;
    private final ApiAutomationRunnerResultSanitizer runnerResultSanitizer;
    private final ApiAutomationRunTargetGuard runTargetGuard;
    private final ApiAutomationRunSecretResolver runSecretResolver;
    private final ApiAutomationScriptBundleFactory scriptBundleFactory;
    private final ApiAutomationResponseMapper responseMapper;
    private final ApiAutomationJsonSupport jsonSupport;
    private final ApiAutomationGenerationSupport generationSupport;
    private final ApiAutomationSyncSupport syncSupport;
    private final ApiAutomationRunSupport runSupport;
    private final AsyncTaskNotificationService notificationService;

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
        this(
                repository,
                runnerPort,
                parser,
                properties,
                contextClient,
                actorResolver,
                assetApiService,
                assetTestCaseService,
                modelInvocationService,
                modelOutputParser,
                objectMapper,
                new AsyncTaskNotificationService((userId, type, title, body, link, metadata) -> {
                })
        );
    }

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
            ObjectMapper objectMapper,
            AsyncTaskNotificationService notificationService
    ) {
        this(
                repository,
                runnerPort,
                parser,
                properties,
                contextClient,
                actorResolver,
                assetApiService,
                assetTestCaseService,
                modelInvocationService,
                modelOutputParser,
                objectMapper,
                notificationService,
                List.of()
        );
    }

    @Autowired
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
            ObjectMapper objectMapper,
            AsyncTaskNotificationService notificationService,
            ObjectProvider<SecretProvider> secretProviders
    ) {
        this(
                repository,
                runnerPort,
                parser,
                properties,
                contextClient,
                actorResolver,
                assetApiService,
                assetTestCaseService,
                modelInvocationService,
                modelOutputParser,
                objectMapper,
                notificationService,
                secretProviders == null ? List.of() : secretProviders.orderedStream().toList()
        );
    }

    ApiAutomationService(
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
            ObjectMapper objectMapper,
            AsyncTaskNotificationService notificationService,
            List<SecretProvider> secretProviders
    ) {
        this.repository = repository;
        this.runnerPort = runnerPort;
        this.parser = parser;
        this.properties = properties;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.runnerResultSanitizer = new ApiAutomationRunnerResultSanitizer(properties, objectMapper);
        this.runTargetGuard = new ApiAutomationRunTargetGuard(properties);
        this.runSecretResolver = new ApiAutomationRunSecretResolver(secretProviders);
        this.jsonSupport = new ApiAutomationJsonSupport(objectMapper);
        this.scriptBundleFactory = new ApiAutomationScriptBundleFactory(objectMapper);
        this.responseMapper = new ApiAutomationResponseMapper(objectMapper);
        this.notificationService = notificationService;
        this.generationSupport = new ApiAutomationGenerationSupport(
                repository,
                assetTestCaseService,
                modelInvocationService,
                modelOutputParser,
                properties,
                jsonSupport
        );
        this.syncSupport = new ApiAutomationSyncSupport(repository, assetApiService, jsonSupport);
        this.runSupport = new ApiAutomationRunSupport(runnerPort, properties, runnerResultSanitizer, jsonSupport);
    }

    public ApiAutomationHealthResponse health() {
        boolean sandboxEnabled = properties.dockerSandboxEnabled();
        String sandboxImage = properties.effectiveRunnerSandboxImage();
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
                        Map.entry("runnerMode", properties.effectiveRunnerMode()),
                        Map.entry("runnerExecutionIsolation", sandboxEnabled ? "DOCKER_SANDBOX" : "HOST_PROCESS_OR_MANAGED"),
                        Map.entry("runnerSandboxEnabled", sandboxEnabled),
                        Map.entry("runnerSandboxReady", sandboxEnabled && StringUtils.hasText(sandboxImage)),
                        Map.entry("runnerSandboxImageConfigured", StringUtils.hasText(sandboxImage)),
                        Map.entry("runnerSandboxNetwork",
                                sandboxEnabled ? properties.effectiveRunnerSandboxNetwork() : "disabled"),
                        Map.entry("runnerArtifactMaxBytes", properties.effectiveRunnerArtifactMaxBytes()),
                        Map.entry("runnerAllowedBaseUrlConfigured", runTargetGuard.allowedBaseUrlConfigured()),
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
        String specDigest = SensitiveTextSanitizer.sha256Hex(content);
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
                SensitiveTextSanitizer.boundedText(command.name(), 128),
                SensitiveTextSanitizer.boundedNullableText(command.versionLabel(), 64),
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
                .map(responseMapper::toSpecResponse)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countSpecs(query));
    }

    @Transactional(readOnly = true)
    public ApiAutomationSpecDetailResponse specDetail(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        return responseMapper.toDetail(spec, repository.endpointSnapshots(id));
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
    public ApiAutomationSpecDetailResponse archiveSpec(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        if ("ARCHIVED".equals(spec.status())) {
            return responseMapper.toDetail(spec, repository.endpointSnapshots(id));
        }
        ApiAutomationSpec archivedSpec = markArchived(spec, actorResolver.currentActor());
        // 归档只关闭后续 parse/diff/sync/generation 入口，保留脱敏 spec 与 endpoint 证据供审计回看。
        repository.archiveSpec(archivedSpec);
        audit("api_automation.spec.archived", archivedSpec, "SUCCESS", Map.of(
                "previousStatus", spec.status(),
                "endpointCount", archivedSpec.endpointCount()
        ));
        return responseMapper.toDetail(archivedSpec, repository.endpointSnapshots(id));
    }

    @Transactional
    public ApiAutomationDiffResponse diffSpec(UUID id) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        // Diff 结果是运营确认同步前的证据快照，必须落库以便审计和前端复核。
        List<ApiAutomationEndpointSnapshot> endpoints = syncSupport.evaluateAndPersistDiff(spec);
        audit("api_automation.api_diffed", spec, "SUCCESS", Map.of(
                "counts", syncSupport.countDiffStatuses(endpoints),
                "endpointCount", endpoints.size()
        ));
        return new ApiAutomationDiffResponse(spec.id(), syncSupport.countDiffStatuses(endpoints), endpoints.stream()
                .map(responseMapper::toEndpointResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public ApiAutomationSyncPreviewResponse syncPreview(UUID id) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        // Sync preview 是只读 dry-run 契约：复用同步前匹配逻辑，但不写 WP3、不更新 endpoint snapshot。
        List<ApiAutomationEndpointSnapshot> endpoints = syncSupport.evaluateDiff(spec);
        List<ApiAutomationSyncPreviewItemResponse> items = endpoints.stream()
                .map(endpoint -> syncSupport.syncPreviewItem(spec, endpoint))
                .toList();
        return new ApiAutomationSyncPreviewResponse(
                spec.id(),
                syncSupport.countSyncPreviewActions(items),
                items,
                endpoints.stream().map(responseMapper::toEndpointResponse).toList(),
                Map.of(
                        "dryRun", true,
                        "wp3Write", false,
                        "endpointSnapshotWrite", false,
                        "aggregateOnly", true,
                        "rawSchemaStored", false,
                        "rawRequestResponseStored", false
                )
        );
    }

    @Transactional
    public ApiAutomationSyncResponse syncSpec(UUID id, SyncApiAutomationSpecCommand command) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        SyncApiAutomationSpecCommand safeCommand = command == null
                ? new SyncApiAutomationSpecCommand(List.of(), true)
                : command;
        Set<UUID> selectedEndpointIds = safeCommand.endpointIdSet();
        // 同步前重新 diff，避免基于前端旧状态写入 WP3；单 endpoint 业务失败在 syncEndpoint 内收敛为明细。
        List<ApiAutomationEndpointSnapshot> diffedEndpoints = syncSupport.evaluateAndPersistDiff(spec);
        List<ApiAutomationSyncItemResponse> items = new ArrayList<>();
        List<ApiAutomationEndpointSnapshot> updatedEndpoints = new ArrayList<>();
        Instant now = Instant.now();
        for (ApiAutomationEndpointSnapshot endpoint : diffedEndpoints) {
            if (!selectedEndpointIds.isEmpty() && !selectedEndpointIds.contains(endpoint.id())) {
                updatedEndpoints.add(endpoint);
                continue;
            }
            ApiAutomationSyncSupport.SyncAttempt attempt = syncSupport.syncEndpoint(spec, endpoint, safeCommand, now);
            items.add(attempt.response());
            updatedEndpoints.add(attempt.snapshot());
        }
        Map<String, Integer> counts = syncSupport.countSyncResults(items);
        audit("api_automation.api_synced", spec, syncSupport.syncResult(counts), Map.of(
                "counts", counts,
                "requestedEndpointCount", selectedEndpointIds.isEmpty() ? diffedEndpoints.size() : selectedEndpointIds.size()
        ));
        return new ApiAutomationSyncResponse(spec.id(), counts, items, updatedEndpoints.stream()
                .map(responseMapper::toEndpointResponse)
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
        ApiAutomationGenerationSupport.GenerationRequest normalized = generationSupport.normalizeRequest(projectId, command);
        // requestKey 面向运营台重试幂等；相同 key 只能绑定同一份规范化 payload，避免覆盖历史生成证据。
        ApiAutomationGenerationTask existing = generationSupport.existingGenerationTask(normalized);
        if (existing != null) {
            return generationTaskDetail(existing.id());
        }
        List<ApiAutomationEndpointSnapshot> targets = generationSupport.generationTargets(spec, normalized.assetApiIds());
        List<ApiAutomationGenerationSupport.GenerationSourceTestCase> sourceTestCases = generationSupport.sourceTestCases(normalized, targets);
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationGenerationSupport.GenerationAttempt attempt = generationSupport.generationAttempt(spec, targets, normalized, sourceTestCases, actor);
        List<ApiAutomationCase> cases = attempt.cases();
        ApiAutomationGenerationTask task = new ApiAutomationGenerationTask(
                UUID.randomUUID(),
                projectId,
                spec.id(),
                normalized.requestKey(),
                normalized.requestDigest(),
                normalized.generationMode(),
                jsonSupport.writeJson(normalized.coverageTypes()),
                "COMPLETED",
                properties.promptKey(),
                attempt.promptVersion(),
                attempt.modelInvocationId(),
                attempt.fallbackUsed(),
                targets.size(),
                cases.size(),
                jsonSupport.writeJson(generationSupport.inputSummary(spec, normalized, targets, sourceTestCases, attempt)),
                attempt.errorSummary(),
                actor,
                actor,
                now,
                now
        );
        repository.insertGenerationTask(task);
        List<ApiAutomationCase> persistedCases = cases.stream()
                .map(value -> generationSupport.caseWithTask(value, task.id(), now))
                .toList();
        persistedCases.forEach(repository::insertAutomationCase);
        ApiAutomationScriptBundle bundle = scriptBundleFactory.createScriptBundle(task, persistedCases, actor, now);
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
        notificationService.notifyApiAutomationGenerationTaskFinished(task);
        return responseMapper.toGenerationTaskDetail(task, persistedCases, List.of(bundle));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiAutomationGenerationTaskResponse> generationTasks(ApiAutomationGenerationTaskPageRequest request) {
        ApiAutomationGenerationTaskQuery query = canonicalGenerationTaskQuery(request.toQuery());
        /*
         * The history list is an aggregate control-plane view. It intentionally excludes generated case bodies,
         * script file details and raw model payloads; callers can fetch one task detail when they need drill-down.
         */
        List<ApiAutomationGenerationTaskResponse> items = repository.generationTasks(query).stream()
                .map(responseMapper::toGenerationTaskResponse)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countGenerationTasks(query));
    }

    @Transactional(readOnly = true)
    public ApiAutomationGenerationTaskDetailResponse generationTaskDetail(UUID id) {
        ApiAutomationGenerationTask task = repository.generationTask(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + id));
        return responseMapper.toGenerationTaskDetail(task, repository.automationCases(id), repository.scriptBundles(id));
    }

    @Transactional
    public ApiAutomationScriptBundleResponse generateScriptBundle(UUID taskId) {
        ApiAutomationGenerationTask task = repository.generationTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + taskId));
        List<ApiAutomationScriptBundle> existingBundles = repository.scriptBundles(taskId).stream()
                .filter(bundle -> !"ARCHIVED".equals(bundle.status()))
                .toList();
        if (!existingBundles.isEmpty()) {
            return responseMapper.toScriptBundleResponse(existingBundles.getFirst());
        }
        List<ApiAutomationCase> cases = repository.automationCases(taskId);
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "生成任务没有可打包的自动化用例草稿");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationScriptBundle bundle = scriptBundleFactory.createScriptBundle(task, cases, actor, now);
        repository.insertScriptBundle(bundle);
        auditScriptBundle(bundle, "SUCCESS", "GENERATED", Map.of(
                "taskId", task.id().toString(),
                "fileCount", bundle.fileCount(),
                "staticCheckStatus", bundle.staticCheckStatus()
        ));
        return responseMapper.toScriptBundleResponse(bundle);
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
        if (!ApiAutomationScriptBundleFactory.STATIC_CHECK_PASSED.equals(bundle.staticCheckStatus())) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    ApiAutomationScriptBundleFactory.STATIC_CHECK_FAILED + ": 脚本静态校验未通过"
            );
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationScriptBundle updated = scriptBundleWithReview(
                bundle,
                "REVIEWING",
                SensitiveTextSanitizer.boundedNullableText(command == null ? null : command.note(), 512),
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
        return responseMapper.toScriptBundleResponse(updated);
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
        return responseMapper.toScriptBundleResponse(updated);
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
        String note = SensitiveTextSanitizer.boundedNullableText(command == null ? null : command.note(), 512);
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
        return responseMapper.toScriptBundleResponse(updated);
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
        ApiAutomationRunTargetGuard.RunTarget target = runTargetGuard.validateRunTarget(command.baseUrl());
        ApiAutomationRunSecretResolver.RunSecretRefs secretRefs =
                runSecretResolver.validateRunSecretRefs(command.secretRefs());
        int timeoutSeconds = properties.effectiveRunnerTimeoutSeconds(command.timeoutSeconds());
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UUID runId = UUID.randomUUID();

        /*
         * Runner admission is a control-plane safety gate: every attempt is persisted with a sanitized target digest
         * and host, while the raw baseUrl and runtime output are discarded after validation.
         */
        ApiAutomationRunSupport.RunBlock block = runSupport.runBlockReason(bundle, cases, target);
        if (block != null) {
            ApiAutomationRun run = runSupport.newRun(
                    runId,
                    bundle,
                    command,
                    target,
                    "BLOCKED",
                    timeoutSeconds,
                    cases.size(),
                    properties.runnerEnabled() ? "NOOP" : "DISABLED",
                    null,
                    block.errorCode(),
                    block.errorSummary(),
                    actor,
                    now,
                    now,
                    now
            );
            repository.insertRun(run);
            List<ApiAutomationRunResult> results = runSupport.blockedRunResults(run, cases, block, now);
            results.forEach(repository::insertRunResult);
            auditRun(run, "FAILED", "BLOCKED", Map.of(
                    "errorCode", block.errorCode(),
                    "caseCount", cases.size(),
                    "baseUrlHost", target.host(),
                    "secretRefCount", secretRefs.count(),
                    "secretRefDigests", secretRefs.digests()
            ));
            notificationService.notifyApiAutomationRunFinished(run);
            return responseMapper.toRunDetail(run, results);
        }

        List<ApiAutomationRunnerPort.RunnerSecret> runnerSecrets =
                runSecretResolver.resolveRunSecrets(secretRefs, bundle.projectId());
        ApiAutomationRunnerPort.RunnerRunResult rawAttempt = runnerPort.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                runId,
                bundle,
                cases,
                target.normalizedBaseUrl(),
                timeoutSeconds,
                secretRefs.digests(),
                runnerSecrets
        ));
        ApiAutomationRunnerPort.RunnerRunResult attempt = runnerResultSanitizer.enforceRunnerArtifactLimit(rawAttempt);
        String status = runSupport.normalizeRunStatus(attempt.status());
        String runnerMode = runSupport.normalizeRunnerMode(attempt.runnerMode());
        // Asynchronous runners stay open until a later callback/worker update closes the platform run.
        boolean activeRun = runSupport.activeRunStatus(status);
        Instant completedAt = activeRun ? null : Instant.now();
        ApiAutomationRun run = runSupport.newRun(
                runId,
                bundle,
                command,
                target,
                status,
                timeoutSeconds,
                cases.size(),
                runnerMode,
                attempt.externalRunId(),
                SensitiveTextSanitizer.boundedNullableText(attempt.errorCode(), 64),
                runnerResultSanitizer.safeRunnerErrorSummary(attempt.errorSummary(), target.normalizedBaseUrl()),
                actor,
                now,
                now,
                completedAt
        );
        repository.insertRun(run);
        List<ApiAutomationRunResult> results = activeRun
                ? List.of()
                : runSupport.runnerResults(run, cases, attempt, target.normalizedBaseUrl(), completedAt);
        results.forEach(repository::insertRunResult);
        auditRun(run, "SUCCESS", "STARTED", Map.of(
                "runnerMode", run.runnerMode(),
                "caseCount", run.caseCount(),
                "secretRefCount", secretRefs.count(),
                "secretRefDigests", secretRefs.digests()
        ));
        if (!activeRun) {
            auditRun(run, runSupport.runAuditResult(status), "COMPLETED", Map.of(
                    "status", run.status(),
                    "runnerMode", run.runnerMode(),
                    "caseCount", run.caseCount(),
                    "resultCount", results.size(),
                    "secretRefCount", secretRefs.count(),
                    "secretRefDigests", secretRefs.digests()
            ));
            notificationService.notifyApiAutomationRunFinished(run);
        }
        return responseMapper.toRunDetail(run, results);
    }

    @Transactional(readOnly = true)
    public ApiAutomationRunDetailResponse runDetail(UUID id) {
        ApiAutomationRun run = repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化运行任务不存在: " + id));
        return responseMapper.toRunDetail(run, repository.runResults(id));
    }

    @Transactional
    public ApiAutomationRunDetailResponse cancelRun(UUID id) {
        ApiAutomationRun run = repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化运行任务不存在: " + id));
        if (!runSupport.cancelableRunStatus(run.status())) {
            auditRun(run, "SUCCESS", "CANCEL_SKIPPED", Map.of(
                    "status", run.status(),
                    "terminal", true
            ));
            return responseMapper.toRunDetail(run, repository.runResults(id));
        }

        // Reuse the opaque external handle so downstream workers can cancel the real execution when one exists.
        ApiAutomationRunnerPort.RunnerCancelResult cancelResult = runnerPort.cancel(new ApiAutomationRunnerPort.RunnerCancelRequest(
                id,
                run.externalRunId(),
                run.runnerMode()
        ));
        boolean accepted = cancelResult != null && cancelResult.accepted();
        if (!accepted) {
            auditRun(run, "FAILED", "CANCEL_REJECTED", runSupport.cancelAuditPayload(run, cancelResult));
            return responseMapper.toRunDetail(run, repository.runResults(id));
        }

        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationRun canceled = runSupport.runWithCancel(
                run,
                SensitiveTextSanitizer.boundedNullableText(cancelResult.errorCode(), 64),
                runnerResultSanitizer.safeRunnerErrorSummary(cancelResult.errorSummary()),
                actor,
                now
        );
        repository.updateRunCancel(canceled);
        ApiAutomationRun persisted = repository.run(id).orElse(canceled);
        auditRun(persisted, "SUCCESS", "CANCELED", Map.of(
                "previousStatus", run.status(),
                "status", persisted.status()
        ));
        notificationService.notifyApiAutomationRunFinished(persisted);
        return responseMapper.toRunDetail(persisted, repository.runResults(id));
    }

    @Transactional
    public ApiAutomationRunExportResponse exportRun(UUID id) {
        ApiAutomationRun run = repository.run(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化运行任务不存在: " + id));
        List<ApiAutomationRunResult> results = repository.runResults(id);
        Map<String, Integer> counts = responseMapper.resultCounts(results);
        // 运行导出用于合规留存和运营排障，只复用脱敏摘要视图；导出动作本身也必须落审计。
        ApiAutomationRunExportResponse response = new ApiAutomationRunExportResponse(
                "wp6-run-export-v1",
                Instant.now(),
                responseMapper.toRunResponse(run),
                results.stream().map(responseMapper::toRunResultResponse).toList(),
                counts,
                responseMapper.runExportRedactionPolicy()
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
        List<UUID> requested = ApiAutomationJsonSupport.normalizedUuidList(requestedCaseIds);
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
                    jsonSupport.writeJson(parseResult.summary()),
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
            return responseMapper.toDetail(parsedSpec, snapshots);
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

    private ApiAutomationSpec markArchived(ApiAutomationSpec spec, String actor) {
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
                spec.sanitizedSpecJson(),
                spec.parseSummaryJson(),
                "ARCHIVED",
                spec.parserVersion(),
                spec.endpointCount(),
                spec.parseErrorSummary(),
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
                jsonSupport.writeJson(Map.of(
                        "parserVersion", OpenApiSpecParser.PARSER_VERSION,
                        "endpointCount", 0,
                        "parseFailed", true,
                        "aggregateOnly", true
                )),
                "PARSE_FAILED",
                OpenApiSpecParser.PARSER_VERSION,
                0,
                SensitiveTextSanitizer.boundedText(message, ERROR_SUMMARY_MAX_CHARS),
                spec.createdBy(),
                actor,
                null,
                spec.createdAt(),
                now
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
        String note = SensitiveTextSanitizer.boundedNullableText(command == null ? null : command.note(), 512);
        return StringUtils.hasText(note) ? note : bundle.reviewNote();
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

    private ApiAutomationGenerationTaskQuery canonicalGenerationTaskQuery(ApiAutomationGenerationTaskQuery query) {
        if (!StringUtils.hasText(query.projectId())) {
            return query;
        }
        // 与规格列表保持一致，允许前端用项目 ID 或编码查询，但持久化过滤始终使用规范项目资源 ID。
        String projectId = contextClient.projectContext(query.projectId()).resourceId();
        return new ApiAutomationGenerationTaskQuery(
                projectId,
                query.specId(),
                query.status(),
                query.limit(),
                query.offset()
        );
    }

    private String sanitizeSourceRef(String sourceRef) {
        if (!StringUtils.hasText(sourceRef)) {
            return null;
        }
        String trimmed = sourceRef.trim();
        int queryIndex = trimmed.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        return SensitiveTextSanitizer.boundedText(withoutQuery, 512);
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
        payload.put("externalRunIdPresent", org.springframework.util.StringUtils.hasText(run.externalRunId()));
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

}
