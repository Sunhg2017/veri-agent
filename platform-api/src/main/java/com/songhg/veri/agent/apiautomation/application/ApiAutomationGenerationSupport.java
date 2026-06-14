package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.asset.application.AssetTestCaseService;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Owns WP6 API automation case generation request normalization, model invocation, and deterministic fallback drafts.
 */
final class ApiAutomationGenerationSupport {

    private static final int ERROR_SUMMARY_MAX_CHARS = 512;
    private static final int GENERATION_CASE_COUNT_MAX = 5;
    private static final int GENERATION_SOURCE_TEST_CASE_MAX = 20;
    private static final int GENERATION_SOURCE_STEP_MAX = 3;
    private static final int GENERATION_SOURCE_TEXT_MAX_CHARS = 160;
    private static final Set<String> GENERATION_MODES = Set.of("FALLBACK_ONLY", "MODEL_WITH_FALLBACK");
    private static final Set<String> COVERAGE_TYPES = Set.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final String WP6_MODEL_SCHEMA_MARKER = "WP6_API_AUTOMATION_GENERATION_V1";
    private static final String MODEL_CALLER_SERVICE = "wp6-api-automation";
    private static final String MODEL_CAPABILITY_JSON = "JSON";
    private static final String DEFAULT_MODEL_SENSITIVITY_LEVEL = "INTERNAL";

    private final ApiAutomationRepository repository;
    private final AssetTestCaseService assetTestCaseService;
    private final ModelInvocationService modelInvocationService;
    private final ApiAutomationModelOutputParser modelOutputParser;
    private final ApiAutomationProperties properties;
    private final ApiAutomationJsonSupport jsonSupport;

    ApiAutomationGenerationSupport(
            ApiAutomationRepository repository,
            AssetTestCaseService assetTestCaseService,
            ModelInvocationService modelInvocationService,
            ApiAutomationModelOutputParser modelOutputParser,
            ApiAutomationProperties properties,
            ApiAutomationJsonSupport jsonSupport
    ) {
        this.repository = repository;
        this.assetTestCaseService = assetTestCaseService;
        this.modelInvocationService = modelInvocationService;
        this.modelOutputParser = modelOutputParser;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
    }

    GenerationRequest normalizeRequest(String projectId, CreateApiAutomationGenerationTaskCommand command) {
        List<String> coverageTypes = normalizeCoverageTypes(command.coverageTypes());
        String generationMode = normalizeGenerationMode(command.generationMode());
        int caseCountPerApi = normalizeCaseCountPerApi(command.caseCountPerApi(), coverageTypes.size());
        List<UUID> assetApiIds = ApiAutomationJsonSupport.normalizedUuidList(command.assetApiIds());
        List<UUID> assetTestCaseIds = ApiAutomationJsonSupport.normalizedUuidList(command.assetTestCaseIds());
        if (assetTestCaseIds.size() > GENERATION_SOURCE_TEST_CASE_MAX) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "assetTestCaseIds 最多支持 " + GENERATION_SOURCE_TEST_CASE_MAX + " 个"
            );
        }
        String requestKey = SensitiveTextSanitizer.boundedNullableText(command.requestKey(), 128);
        Map<String, Object> digestPayload = new LinkedHashMap<>();
        digestPayload.put("projectId", projectId);
        digestPayload.put("specId", command.specId().toString());
        digestPayload.put("assetApiIds", ApiAutomationJsonSupport.uuidStrings(assetApiIds));
        digestPayload.put("assetTestCaseIds", ApiAutomationJsonSupport.uuidStrings(assetTestCaseIds));
        digestPayload.put("coverageTypes", coverageTypes);
        digestPayload.put("generationMode", generationMode);
        digestPayload.put("caseCountPerApi", caseCountPerApi);
        String requestDigest = SensitiveTextSanitizer.sha256Hex(jsonSupport.writeJson(digestPayload));
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

    ApiAutomationGenerationTask existingGenerationTask(GenerationRequest request) {
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

    List<ApiAutomationEndpointSnapshot> generationTargets(
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

    List<GenerationSourceTestCase> sourceTestCases(
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

    GenerationAttempt generationAttempt(
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

    ApiAutomationCase caseWithTask(ApiAutomationCase automationCase, UUID taskId, Instant now) {
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

    Map<String, Object> inputSummary(
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
        summary.put("assetTestCaseIds", ApiAutomationJsonSupport.uuidStrings(request.assetTestCaseIds()));
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
                        : SensitiveTextSanitizer.boundedNullableText(fallbackReason, ERROR_SUMMARY_MAX_CHARS)
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
                        endpoint -> ApiAutomationJsonSupport.assetKey(endpoint.httpMethod(), endpoint.path()),
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
            endpoint = endpointByMethodPath.get(ApiAutomationJsonSupport.assetKey(generatedCase.method(), generatedCase.path()));
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
                SensitiveTextSanitizer.boundedText(generatedCase.title(), 256),
                endpoint.httpMethod(),
                endpoint.path(),
                generatedCase.coverageType(),
                generatedCase.expectedStatus(),
                jsonSupport.writeJson(modelAssertionSummary(endpoint, generatedCase)),
                jsonSupport.writeJson(modelRequestTemplate(endpoint, generatedCase)),
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
        summary.put("rationale", ApiAutomationJsonSupport.safeSourceText(generatedCase.rationale(), GENERATION_SOURCE_TEXT_MAX_CHARS));
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
        return jsonSupport.writeJson(payload);
    }

    private Map<String, Object> endpointModelPayload(ApiAutomationEndpointSnapshot endpoint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("endpointSnapshotId", endpoint.id().toString());
        item.put("assetApiId", endpoint.assetApiId() == null ? null : endpoint.assetApiId().toString());
        item.put("method", endpoint.httpMethod());
        item.put("path", endpoint.path());
        item.put("summary", ApiAutomationJsonSupport.safeSourceText(endpoint.summary(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        item.put("operationId", ApiAutomationJsonSupport.safeSourceText(endpoint.operationId(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        item.put("tags", ApiAutomationJsonSupport.safeSourceText(endpoint.tags(), GENERATION_SOURCE_TEXT_MAX_CHARS));
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
            return SensitiveTextSanitizer.boundedText(businessException.getErrorCode().name() + ": " + businessException.getMessage(),
                    ERROR_SUMMARY_MAX_CHARS);
        }
        return SensitiveTextSanitizer.boundedText("MODEL_GENERATION_FAILED: " + (StringUtils.hasText(root.getMessage())
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
                SensitiveTextSanitizer.boundedText("[" + coverageType + "] " + endpoint.httpMethod() + " " + endpoint.path(), 256),
                endpoint.httpMethod(),
                endpoint.path(),
                coverageType,
                expectedStatus,
                jsonSupport.writeJson(assertionSummary(endpoint, coverageType, expectedStatus)),
                jsonSupport.writeJson(requestTemplate(endpoint)),
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
        summary.put("code", ApiAutomationJsonSupport.safeSourceText(testCase.code(), 64));
        summary.put("title", ApiAutomationJsonSupport.safeSourceText(testCase.title(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        summary.put("status", ApiAutomationJsonSupport.nullToEmpty(testCase.status()));
        summary.put("priority", ApiAutomationJsonSupport.nullToEmpty(testCase.priority()));
        summary.put("tags", ApiAutomationJsonSupport.safeSourceText(testCase.tags(), GENERATION_SOURCE_TEXT_MAX_CHARS));
        summary.put("source", ApiAutomationJsonSupport.nullToEmpty(testCase.source()));
        summary.put("sourceRefDigest", StringUtils.hasText(testCase.sourceRef()) ? SensitiveTextSanitizer.sha256Hex(testCase.sourceRef()) : null);
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
                    summary.put("actionSummary", ApiAutomationJsonSupport.safeSourceText(step.action(), GENERATION_SOURCE_TEXT_MAX_CHARS));
                    summary.put("expectedSummary", ApiAutomationJsonSupport.safeSourceText(step.expectedResult(), GENERATION_SOURCE_TEXT_MAX_CHARS));
                    return summary;
                })
                .toList();
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

    record GenerationRequest(
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

    record GenerationSourceTestCase(
            UUID id,
            UUID apiId,
            Map<String, Object> summary
    ) {
    }

    record GenerationAttempt(
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
