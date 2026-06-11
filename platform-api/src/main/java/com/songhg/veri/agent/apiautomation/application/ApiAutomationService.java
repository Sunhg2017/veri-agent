package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.command.SyncApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.parser.OpenApiParseResult;
import com.songhg.veri.agent.apiautomation.application.parser.ParsedOpenApiEndpoint;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationEndpointSnapshotResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationCaseResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationHealthResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncItemResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecResponse;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final List<String> DIFF_STATUSES = List.of("NEW", "CHANGED", "MATCHED", "CONFLICT", "SKIPPED");
    private static final Set<String> GENERATION_MODES = Set.of("FALLBACK_ONLY", "MODEL_WITH_FALLBACK");
    private static final Set<String> COVERAGE_TYPES = Set.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final List<Pattern> SENSITIVE_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._\\-]{8,}"),
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization)\\s*[:=]\\s*[^\\s,;，；]+"),
            Pattern.compile("(?i)\\b(sk|pk|rk)_[a-z0-9_-]{8,}\\b")
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ApiAutomationRepository repository;
    private final OpenApiSpecParser parser;
    private final ApiAutomationProperties properties;
    private final ApiAutomationPlatformContextClient contextClient;
    private final ApiAutomationActorResolver actorResolver;
    private final AssetApiService assetApiService;
    private final AssetTestCaseService assetTestCaseService;
    private final ObjectMapper objectMapper;

    public ApiAutomationService(
            ApiAutomationRepository repository,
            OpenApiSpecParser parser,
            ApiAutomationProperties properties,
            ApiAutomationPlatformContextClient contextClient,
            ApiAutomationActorResolver actorResolver,
            AssetApiService assetApiService,
            AssetTestCaseService assetTestCaseService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.parser = parser;
        this.properties = properties;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.assetApiService = assetApiService;
        this.assetTestCaseService = assetTestCaseService;
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
                properties.runnerTimeoutSeconds(),
                properties.runnerMaxCases(),
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
                        Map.entry("modelGenerationReady", false),
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
        // 当前 M4 切片尚未接入 WP2 模型调用，先生成明确标记为 FALLBACK 的聚合用例草稿。
        List<ApiAutomationCase> cases = fallbackCases(spec, targets, normalized, sourceTestCases);
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
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
                null,
                null,
                true,
                targets.size(),
                cases.size(),
                writeJson(inputSummary(spec, normalized, targets, sourceTestCases, cases)),
                null,
                actor,
                actor,
                now,
                now
        );
        repository.insertGenerationTask(task);
        cases.stream()
                .map(value -> caseWithTask(value, task.id(), now))
                .forEach(repository::insertAutomationCase);
        auditGenerationTask(task, "SUCCESS", Map.of(
                "apiCount", task.apiCount(),
                "caseCount", task.caseCount(),
                "sourceTestCaseCount", sourceTestCases.size(),
                "generationMode", task.generationMode(),
                "fallbackUsed", task.fallbackUsed(),
                "coverageTypes", normalized.coverageTypes()
        ));
        return generationTaskDetail(task.id());
    }

    @Transactional(readOnly = true)
    public ApiAutomationGenerationTaskDetailResponse generationTaskDetail(UUID id) {
        ApiAutomationGenerationTask task = repository.generationTask(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口自动化生成任务不存在: " + id));
        return toGenerationTaskDetail(task, repository.automationCases(id));
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
            List<ApiAutomationCase> cases
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("specId", spec.id().toString());
        summary.put("apiCount", targets.size());
        summary.put("caseCount", cases.size());
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
        summary.put("modelGenerationReady", false);
        summary.put("fallbackReason", "MODEL_WITH_FALLBACK".equals(request.generationMode())
                ? "MODEL_GENERATION_NOT_WIRED"
                : "DETERMINISTIC_MODE");
        summary.put("rawRequestResponseStored", false);
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
            List<ApiAutomationCase> cases
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
                cases.stream().map(this::toAutomationCaseResponse).toList()
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

    private ApiAutomationSpec requireSpec(UUID id) {
        return repository.spec(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI 规格不存在: " + id));
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

    private record SyncAttempt(
            ApiAutomationEndpointSnapshot snapshot,
            ApiAutomationSyncItemResponse response
    ) {
    }
}
