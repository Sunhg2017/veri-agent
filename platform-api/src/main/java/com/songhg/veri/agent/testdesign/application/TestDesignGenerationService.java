package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TraceLinkResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestDesignGenerationService {

    private static final Set<String> CANDIDATE_PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final String GENERATION_MODE_RULE_TEMPLATE = "RULE_TEMPLATE";
    private static final String GENERATION_MODE_MODEL = "MODEL";
    private static final String GENERATION_MODE_MODEL_WITH_FALLBACK = "MODEL_WITH_FALLBACK";
    private static final String WP5_MODEL_SCHEMA_MARKER = "WP5_TEST_DESIGN_GENERATION_V1";
    private static final String MODEL_CALLER_SERVICE = "wp5-test-design";
    private static final String MODEL_CAPABILITY_JSON = "JSON";
    private static final String DEFAULT_MODEL_SENSITIVITY_LEVEL = "INTERNAL";
    private final AssetService assetService;
    private final TestDesignResponseMapper responseMapper;
    private final TestDesignCandidateQualityGate qualityGate;
    private final ModelInvocationService modelInvocationService;
    private final TestDesignModelOutputParser modelOutputParser;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignGenerationService(
            AssetService assetService,
            TestDesignResponseMapper responseMapper,
            TestDesignCandidateQualityGate qualityGate,
            ModelInvocationService modelInvocationService,
            TestDesignModelOutputParser modelOutputParser,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.assetService = assetService;
        this.responseMapper = responseMapper;
        this.qualityGate = qualityGate;
        this.modelInvocationService = modelInvocationService;
        this.modelOutputParser = modelOutputParser;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    TestDesignGenerationContext generationContext(
            String projectId,
            List<RequirementResponse> requirements,
            ExplicitContextAssetIds explicitContext
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contextVersion", "wp5-context-v1");
        summary.put("projectId", projectId);
        summary.put("promptKey", properties.promptKey());
        summary.put("promptVersion", properties.promptVersion());
        summary.put("generationMode", properties.generationMode());
        summary.put("requirements", requirements.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(this::requirementContextSummary)
                .toList());
        summary.put("linkedAssetsByRequirement", requirements.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(requirement -> linkedAssetContextSummary(projectId, requirement))
                .toList());
        summary.put("existingCasesByRequirement", requirements.stream()
                .sorted(Comparator.comparing(RequirementResponse::id))
                .map(requirement -> existingCaseContextSummary(projectId, requirement.id()))
                .toList());
        summary.put("explicitAssets", explicitAssetContextSummary(projectId, explicitContext));
        summary.put("limits", contextSummaryLimits());
        summary.put("assemblyPolicy", TestDesignContextAssemblyPolicy.snapshot());
        summary.put("policyGovernance", TestDesignContextPolicyGovernance.snapshot());
        summary.put("policyOperations", TestDesignContextPolicyOperations.snapshot());
        summary.put("scopePolicy", TestDesignScopePolicy.snapshot());
        try {
            String contextSummaryJson = objectMapper.writeValueAsString(summary);
            return new TestDesignGenerationContext(sha256(contextSummaryJson), contextSummaryJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WP5 generation context summary serialization failed", exception);
        }
    }

    /**
     * Builds the model-ready requirement summary from WP3/WP4-facing fields only.
     *
     * <p>The summary is deliberately redacted and truncated before it is persisted; future WP2 prompt assembly can
     * reuse the same digest to explain exactly which source snapshot produced a task without storing raw prompt text.
     */
    private Map<String, Object> requirementContextSummary(RequirementResponse requirement) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", requirement.id().toString());
        item.put("code", redactedPreview(requirement.code(), 80));
        item.put("title", redactedPreview(requirement.title(), 160));
        item.put("priority", requirement.priority());
        item.put("status", requirement.status());
        item.put("source", redactedPreview(requirement.source(), 80));
        item.put("sourceRef", redactedPreview(requirement.sourceRef(), 160));
        item.put("version", requirement.version());
        item.put("descriptionPreview", redactedPreview(requirement.description(), contextRequirementDescriptionChars()));
        item.put("acceptanceCriteriaPreview", redactedPreview(
                requirement.acceptanceCriteria(),
                contextAcceptanceCriteriaChars()
        ));
        item.put("tags", summaryTags(requirement.tags()));
        return item;
    }

    /**
     * Reads linked API/page/flow summaries only through WP3 application services.
     *
     * <p>Trace links may outlive archived or deleted assets, so stale targets are omitted from the model context
     * instead of failing generation for an otherwise valid requirement.
     */
    private Map<String, Object> linkedAssetContextSummary(String projectId, RequirementResponse requirement) {
        List<TraceLinkResponse> links = linksByRequirement(requirement.id());
        List<ApiResponseDTO> apis = links.stream()
                .map(TraceLinkResponse::apiId)
                .filter(Objects::nonNull)
                .distinct()
                .map(apiId -> activeApi(apiId, projectId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(ApiResponseDTO::code, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ApiResponseDTO::id))
                .toList();
        List<com.songhg.veri.agent.asset.application.view.PageResponse> pages = links.stream()
                .map(TraceLinkResponse::pageId)
                .filter(Objects::nonNull)
                .distinct()
                .map(pageId -> activePage(pageId, projectId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(
                                com.songhg.veri.agent.asset.application.view.PageResponse::code,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(com.songhg.veri.agent.asset.application.view.PageResponse::id))
                .toList();
        List<BusinessFlowResponse> flows = links.stream()
                .map(TraceLinkResponse::flowId)
                .filter(Objects::nonNull)
                .distinct()
                .map(flowId -> activeBusinessFlow(flowId, projectId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(BusinessFlowResponse::code, Comparator.nullsLast(String::compareTo))
                        .thenComparing(BusinessFlowResponse::id))
                .toList();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requirementId", requirement.id().toString());
        item.put("apiCount", apis.size());
        item.put("pageCount", pages.size());
        item.put("flowCount", flows.size());
        item.put("apis", apis.stream().limit(contextLinkedAssetsPerRequirement()).map(this::apiContextSummary).toList());
        item.put("pages", pages.stream().limit(contextLinkedAssetsPerRequirement()).map(this::pageContextSummary).toList());
        item.put("flows", flows.stream().limit(contextLinkedAssetsPerRequirement()).map(this::businessFlowContextSummary).toList());
        return item;
    }

    /**
     * Summarizes assets that the requester explicitly adds to the generation context.
     *
     * <p>These assets do not need a WP3 trace link to every selected requirement, but they still must belong to the
     * task project and are persisted only as redacted previews so task replay can explain the input without storing raw
     * API schemas, page trees, flow JSON or prompt text.
     */
    private Map<String, Object> explicitAssetContextSummary(String projectId, ExplicitContextAssetIds explicitContext) {
        List<ApiResponseDTO> apis = explicitContext.apiIds().stream()
                .map(apiId -> activeApi(apiId, projectId))
                .map(optional -> optional.orElseThrow(() ->
                        new BusinessException(ErrorCode.VALIDATION_ERROR, "显式上下文 API 不存在或不属于当前项目")))
                .sorted(Comparator.comparing(ApiResponseDTO::code, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ApiResponseDTO::id))
                .toList();
        List<com.songhg.veri.agent.asset.application.view.PageResponse> pages = explicitContext.pageIds().stream()
                .map(pageId -> activePage(pageId, projectId))
                .map(optional -> optional.orElseThrow(() ->
                        new BusinessException(ErrorCode.VALIDATION_ERROR, "显式上下文页面不存在或不属于当前项目")))
                .sorted(Comparator.comparing(
                                com.songhg.veri.agent.asset.application.view.PageResponse::code,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(com.songhg.veri.agent.asset.application.view.PageResponse::id))
                .toList();
        List<BusinessFlowResponse> flows = explicitContext.flowIds().stream()
                .map(flowId -> activeBusinessFlow(flowId, projectId))
                .map(optional -> optional.orElseThrow(() ->
                        new BusinessException(ErrorCode.VALIDATION_ERROR, "显式上下文业务流不存在或不属于当前项目")))
                .sorted(Comparator.comparing(BusinessFlowResponse::code, Comparator.nullsLast(String::compareTo))
                        .thenComparing(BusinessFlowResponse::id))
                .toList();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("apiCount", apis.size());
        item.put("pageCount", pages.size());
        item.put("flowCount", flows.size());
        item.put("apiIds", apis.stream().map(api -> api.id().toString()).toList());
        item.put("pageIds", pages.stream().map(page -> page.id().toString()).toList());
        item.put("flowIds", flows.stream().map(flow -> flow.id().toString()).toList());
        item.put("apis", apis.stream().limit(contextExplicitAssetsPerType()).map(this::apiContextSummary).toList());
        item.put("pages", pages.stream().limit(contextExplicitAssetsPerType()).map(this::pageContextSummary).toList());
        item.put("flows", flows.stream().limit(contextExplicitAssetsPerType()).map(this::businessFlowContextSummary).toList());
        return item;
    }

    private List<TraceLinkResponse> linksByRequirement(UUID requirementId) {
        TraceLinkListRequest request = new TraceLinkListRequest();
        request.setRequirementId(requirementId);
        request.setSize(100);
        return assetService.listLinks(request).items();
    }

    private Optional<ApiResponseDTO> activeApi(UUID apiId, String projectId) {
        try {
            ApiResponseDTO api = assetService.getApi(apiId);
            return sameProject(api.projectId(), projectId) ? Optional.of(api) : Optional.empty();
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Optional<com.songhg.veri.agent.asset.application.view.PageResponse> activePage(
            UUID pageId,
            String projectId
    ) {
        try {
            com.songhg.veri.agent.asset.application.view.PageResponse page = assetService.getPage(pageId);
            return sameProject(page.projectId(), projectId) ? Optional.of(page) : Optional.empty();
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Optional<BusinessFlowResponse> activeBusinessFlow(UUID flowId, String projectId) {
        try {
            BusinessFlowResponse flow = assetService.getBusinessFlow(flowId);
            return sameProject(flow.projectId(), projectId) ? Optional.of(flow) : Optional.empty();
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND == exception.getErrorCode()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Map<String, Object> apiContextSummary(ApiResponseDTO api) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", api.id().toString());
        item.put("code", redactedPreview(api.code(), 80));
        item.put("summary", redactedPreview(api.summary(), 160));
        item.put("method", api.httpMethod());
        item.put("path", redactedPreview(api.path(), 160));
        item.put("status", api.status());
        item.put("source", redactedPreview(api.source(), 80));
        item.put("sourceRef", redactedPreview(api.sourceRef(), 160));
        item.put("version", redactedPreview(api.version(), 80));
        item.put("descriptionPreview", redactedPreview(api.description(), 200));
        item.put("requestSchemaPreview", redactedPreview(api.requestSchema(), contextAssetSchemaChars()));
        item.put("responseSchemaPreview", redactedPreview(api.responseSchema(), contextAssetSchemaChars()));
        return item;
    }

    private Map<String, Object> pageContextSummary(
            com.songhg.veri.agent.asset.application.view.PageResponse page
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", page.id().toString());
        item.put("code", redactedPreview(page.code(), 80));
        item.put("name", redactedPreview(page.name(), 160));
        item.put("urlPattern", redactedPreview(page.urlPattern(), 160));
        item.put("status", page.status());
        item.put("source", redactedPreview(page.source(), 80));
        item.put("sourceRef", redactedPreview(page.sourceRef(), 160));
        item.put("sourceVersion", redactedPreview(page.sourceVersion(), 80));
        item.put("componentTreePreview", redactedPreview(page.componentTree(), contextAssetSchemaChars()));
        item.put("screenshotUrl", redactedPreview(page.screenshotUrl(), 160));
        return item;
    }

    private Map<String, Object> businessFlowContextSummary(BusinessFlowResponse flow) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", flow.id().toString());
        item.put("code", redactedPreview(flow.code(), 80));
        item.put("name", redactedPreview(flow.name(), 160));
        item.put("priority", flow.priority());
        item.put("status", flow.status());
        item.put("descriptionPreview", redactedPreview(flow.description(), 200));
        item.put("flowJsonPreview", redactedPreview(flow.flowJson(), contextAssetSchemaChars()));
        return item;
    }

    private Map<String, Object> existingCaseContextSummary(String projectId, UUID requirementId) {
        List<TestCaseResponse> cases = assetService.findActiveTestCasesByRequirement(projectId, requirementId).stream()
                .sorted(Comparator.comparing(TestCaseResponse::title, Comparator.nullsLast(String::compareTo))
                        .thenComparing(TestCaseResponse::id))
                .toList();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requirementId", requirementId.toString());
        item.put("count", cases.size());
        item.put("cases", cases.stream().limit(contextExistingCasesPerRequirement()).map(this::testCaseContextSummary).toList());
        return item;
    }

    private Map<String, Object> testCaseContextSummary(TestCaseResponse testCase) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", testCase.id().toString());
        item.put("title", redactedPreview(testCase.title(), 160));
        item.put("priority", testCase.priority());
        item.put("status", testCase.status());
        item.put("source", redactedPreview(testCase.source(), 80));
        item.put("sourceRef", redactedPreview(testCase.sourceRef(), 160));
        item.put("stepCount", testCase.steps() == null ? 0 : testCase.steps().size());
        item.put("descriptionPreview", redactedPreview(testCase.description(), 200));
        return item;
    }

    /**
     * Publishes the effective clipping policy with every persisted context summary.
     *
     * <p>The digest is computed after these values are applied, so changing an operations limit intentionally creates a
     * different generation input snapshot and prevents replaying candidates created from a wider or narrower context.
     */
    private Map<String, Object> contextSummaryLimits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.putAll(properties.effectiveContextLimits());
        limits.put("rawPromptStored", false);
        return limits;
    }

    record TestDesignGenerationContext(String inputDigest, String contextSummaryJson) {
    }

    record ExplicitContextAssetIds(List<UUID> apiIds, List<UUID> pageIds, List<UUID> flowIds) {
        ExplicitContextAssetIds {
            apiIds = List.copyOf(apiIds == null ? List.of() : apiIds);
            pageIds = List.copyOf(pageIds == null ? List.of() : pageIds);
            flowIds = List.copyOf(flowIds == null ? List.of() : flowIds);
        }
    }

    /**
     * Produces generated candidates for an already claimed task.
     *
     * <p>This service intentionally does not create tasks, transition task status, persist candidates or write audits.
     * Those lifecycle side effects stay in {@link TestDesignTaskService}; generation only selects the backend, invokes
     * the model when configured, parses output and returns a validated candidate batch.
     */
    GenerationAttempt generateCandidates(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now
    ) {
        GenerationAttempt attempt = generateCandidatesUsingConfiguredMode(task, requirements, coverageTypes, now);
        qualityGate.validateGeneratedBatch(attempt.candidates());
        return attempt;
    }

    /**
     * Selects the generation backend from configuration while keeping the review and publish contracts unchanged.
     *
     * <p>`MODEL` is strict and fails the task when WP2 rejects or returns invalid output. `MODEL_WITH_FALLBACK` records
     * the model failure as a task warning and then uses the deterministic rule template so reviewers still get
     * auditable candidates without mistaking them for pure model output.
     */
    private GenerationAttempt generateCandidatesUsingConfiguredMode(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now
    ) {
        String generationMode = normalizedGenerationMode();
        if (GENERATION_MODE_RULE_TEMPLATE.equals(generationMode)) {
            return templateGenerationAttempt(task, requirements, coverageTypes, now, null);
        }
        if (!GENERATION_MODE_MODEL.equals(generationMode) && !GENERATION_MODE_MODEL_WITH_FALLBACK.equals(generationMode)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不支持的 WP5 生成模式: " + properties.generationMode());
        }
        try {
            return modelGenerationAttempt(task, requirements, coverageTypes, now);
        } catch (TestDesignModelGenerationException exception) {
            if (!GENERATION_MODE_MODEL_WITH_FALLBACK.equals(generationMode)) {
                throw exception;
            }
            TestDesignTask taskWithObservation = exception.response() == null
                    ? task
                    : withModelInvocation(task, exception.response());
            return templateGenerationAttempt(taskWithObservation, requirements, coverageTypes, now,
                    modelFallbackWarning(exception));
        } catch (RuntimeException exception) {
            if (!GENERATION_MODE_MODEL_WITH_FALLBACK.equals(generationMode)) {
                throw exception;
            }
            return templateGenerationAttempt(task, requirements, coverageTypes, now, modelFallbackWarning(exception));
        }
    }

    private GenerationAttempt templateGenerationAttempt(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now,
            String warningMessage
    ) {
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (RequirementResponse requirement : requirements) {
            candidates.addAll(generateCandidates(task, requirement, coverageTypes, null, now));
        }
        return new GenerationAttempt(task, candidates, warningMessage);
    }

    private GenerationAttempt modelGenerationAttempt(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            Instant now
    ) {
        ModelInvocationResult response = null;
        try {
            response = modelInvocationService.invoke(new ModelInvocationCommand(
                    task.projectId(),
                    null,
                    null,
                    task.promptKey(),
                    Map.of("schemaMarker", WP5_MODEL_SCHEMA_MARKER),
                    List.of(new ChatMessage("user", modelGenerationPayload(task, requirements, coverageTypes))),
                    null,
                    null,
                    false,
                    DEFAULT_MODEL_SENSITIVITY_LEVEL,
                    MODEL_CAPABILITY_JSON
            ), new ServicePrincipal(MODEL_CALLER_SERVICE, task.requestedBy()));
            TestDesignTask taskWithInvocation = withModelInvocation(task, response);
            List<TestDesignModelOutputParser.ModelGeneratedCase> generatedCases =
                    modelOutputParser.parse(response.content());
            List<TestDesignCandidate> candidates = candidatesFromModelOutput(
                    taskWithInvocation,
                    requirements,
                    coverageTypes,
                    generatedCases,
                    now
            );
            return new GenerationAttempt(taskWithInvocation, candidates, null);
        } catch (RuntimeException exception) {
            throw new TestDesignModelGenerationException(response, exception);
        }
    }

    private String modelGenerationPayload(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaMarker", WP5_MODEL_SCHEMA_MARKER);
        payload.put("taskId", task.id().toString());
        payload.put("projectId", task.projectId());
        payload.put("coverageTypes", coverageTypes);
        payload.put("caseCountPerRequirement", Math.min(coverageTypes.size(), maxCasesPerRequirement()));
        payload.put("requirements", requirements.stream().map(this::requirementModelPayload).toList());
        payload.put("contextSummary", contextSummaryPayload(task.contextSummaryJson()));
        payload.put("contextPacking", contextPackingPolicy());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WP5 model generation payload serialization failed", exception);
        }
    }

    private Map<String, Object> contextPackingPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.putAll(properties.effectiveContextLimits());
        policy.put("rawPromptStored", false);
        policy.put("persistedContextSummaryOnly", true);
        policy.put("assemblyPolicy", TestDesignContextAssemblyPolicy.snapshot());
        policy.put("policyGovernance", TestDesignContextPolicyGovernance.snapshot());
        policy.put("policyOperations", TestDesignContextPolicyOperations.snapshot());
        policy.put("scopePolicy", TestDesignScopePolicy.snapshot());
        return policy;
    }

    private Map<String, Object> requirementModelPayload(RequirementResponse requirement) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", requirement.id().toString());
        item.put("code", redactedPreview(requirement.code(), 80));
        item.put("title", redactedPreview(requirement.title(), 160));
        item.put("priority", requirement.priority());
        item.put("description", redactedPreview(requirement.description(), 500));
        item.put("acceptanceCriteria", redactedPreview(requirement.acceptanceCriteria(), 500));
        item.put("tags", summaryTags(requirement.tags()));
        return item;
    }

    private Object contextSummaryPayload(String contextSummaryJson) {
        if (!StringUtils.hasText(contextSummaryJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(contextSummaryJson);
        } catch (JsonProcessingException exception) {
            return Map.of("unavailable", true);
        }
    }

    private List<TestDesignCandidate> candidatesFromModelOutput(
            TestDesignTask task,
            List<RequirementResponse> requirements,
            List<String> coverageTypes,
            List<TestDesignModelOutputParser.ModelGeneratedCase> generatedCases,
            Instant now
    ) {
        Map<String, RequirementResponse> requirementIndex = requirementReferenceIndex(requirements);
        Map<UUID, Integer> countsByRequirement = new LinkedHashMap<>();
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (TestDesignModelOutputParser.ModelGeneratedCase generatedCase : generatedCases) {
            if (!coverageTypes.contains(generatedCase.coverageType())) {
                continue;
            }
            RequirementResponse requirement = resolveGeneratedRequirement(generatedCase, requirements, requirementIndex);
            int existingCount = countsByRequirement.getOrDefault(requirement.id(), 0);
            if (existingCount >= maxCasesPerRequirement()) {
                continue;
            }
            candidates.add(candidateFromModelCase(task, requirement, generatedCase, now));
            countsByRequirement.put(requirement.id(), existingCount + 1);
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出未匹配当前任务需求和覆盖类型");
        }
        return candidates;
    }

    private Map<String, RequirementResponse> requirementReferenceIndex(List<RequirementResponse> requirements) {
        Map<String, RequirementResponse> index = new LinkedHashMap<>();
        for (RequirementResponse requirement : requirements) {
            putRequirementReference(index, requirement.id().toString(), requirement);
            putRequirementReference(index, requirement.code(), requirement);
            putRequirementReference(index, requirement.title(), requirement);
        }
        return index;
    }

    private static void putRequirementReference(
            Map<String, RequirementResponse> index,
            String reference,
            RequirementResponse requirement
    ) {
        if (StringUtils.hasText(reference)) {
            index.put(reference.trim().toLowerCase(Locale.ROOT), requirement);
        }
    }

    private RequirementResponse resolveGeneratedRequirement(
            TestDesignModelOutputParser.ModelGeneratedCase generatedCase,
            List<RequirementResponse> requirements,
            Map<String, RequirementResponse> requirementIndex
    ) {
        if (StringUtils.hasText(generatedCase.requirementRef())) {
            RequirementResponse matched = requirementIndex.get(generatedCase.requirementRef().trim().toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        if (requirements.size() == 1) {
            return requirements.getFirst();
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出缺少可解析的 requirementRef: " + generatedCase.title());
    }

    private TestDesignCandidate candidateFromModelCase(
            TestDesignTask task,
            RequirementResponse requirement,
            TestDesignModelOutputParser.ModelGeneratedCase generatedCase,
            Instant now
    ) {
        String title = redactSensitiveText(generatedCase.title());
        String coverageType = normalizeCoverageType(generatedCase.coverageType(), null);
        List<TestDesignStepResponse> steps = generatedCase.steps().stream()
                .map(step -> step(step.stepOrder(), redactSensitiveText(step.action()),
                        redactSensitiveText(step.expectedResult())))
                .toList();
        return new TestDesignCandidate(
                UUID.randomUUID(),
                task.id(),
                task.projectId(),
                requirement.id(),
                firstUuid(generatedCase.apiRefs()),
                title,
                modelCaseDescription(generatedCase),
                coverageType,
                normalizePriority(generatedCase.priority(), priorityFor(requirement.priority(), coverageType)),
                TestDesignCandidateStatus.GENERATED.name(),
                redactSensitiveText(generatedCase.preconditions()),
                stepsJson(steps),
                redactSensitiveText(generatedCase.expectedResult()),
                tagsText(modelCaseTags(generatedCase)),
                duplicateKey(requirement.id(), coverageType, title),
                generatedCase.confidence(),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now
        );
    }

    private static UUID firstUuid(List<String> refs) {
        if (refs == null) {
            return null;
        }
        for (String ref : refs) {
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            try {
                return UUID.fromString(ref.trim());
            } catch (IllegalArgumentException ignored) {
                // Model references may be external API codes; WP5 only stores UUID-backed API links in this slice.
            }
        }
        return null;
    }

    private static String modelCaseDescription(TestDesignModelOutputParser.ModelGeneratedCase generatedCase) {
        List<String> parts = new ArrayList<>();
        addDescriptionPart(parts, generatedCase.description());
        addDescriptionPart(parts, generatedCase.rationale() == null ? null : "依据: " + generatedCase.rationale());
        addDescriptionPart(parts, generatedCase.riskNotes() == null ? null : "风险: " + generatedCase.riskNotes());
        String description = String.join("\n", parts);
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String redacted = redactSensitiveText(description);
        return redacted.length() <= 2000 ? redacted : redacted.substring(0, 1997) + "...";
    }

    private static void addDescriptionPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private static List<String> modelCaseTags(TestDesignModelOutputParser.ModelGeneratedCase generatedCase) {
        List<String> tags = new ArrayList<>();
        if (generatedCase.tags() != null) {
            tags.addAll(generatedCase.tags());
        }
        tags.add("wp5");
        tags.add("ai-generated");
        tags.add("model");
        tags.add(generatedCase.coverageType().toLowerCase(Locale.ROOT));
        return tags;
    }

    private List<TestDesignCandidate> generateCandidates(
            TestDesignTask task,
            RequirementResponse requirement,
            List<String> coverageTypes,
            Integer requestedCount,
            Instant now
    ) {
        int limit = requestedCount == null || requestedCount <= 0 ? maxCasesPerRequirement()
                : Math.min(requestedCount, maxCasesPerRequirement());
        List<String> selectedCoverageTypes = coverageTypes.stream().limit(limit).toList();
        List<TestDesignCandidate> candidates = new ArrayList<>();
        for (String coverageType : selectedCoverageTypes) {
            candidates.add(candidateFromRequirement(task, requirement, coverageType, now));
        }
        return candidates;
    }

    private TestDesignCandidate candidateFromRequirement(
            TestDesignTask task,
            RequirementResponse requirement,
            String coverageType,
            Instant now
    ) {
        UUID id = UUID.randomUUID();
        String requirementTitle = redactSensitiveText(requirement.title());
        String title = switch (coverageType) {
            case "SMOKE" -> "验证" + requirementTitle + "核心冒烟流程";
            case "EXCEPTION" -> "验证" + requirementTitle + "异常提示与阻断";
            case "BOUNDARY" -> "验证" + requirementTitle + "边界条件";
            case "PERMISSION" -> "验证" + requirementTitle + "权限控制";
            case "REGRESSION" -> "回归验证" + requirementTitle;
            default -> "验证" + requirementTitle + "主流程";
        };
        String description = "基于 WP3 需求生成的 " + coverageType + " 候选用例。需求编号: " + requirement.code();
        List<TestDesignStepResponse> steps = templateSteps(requirement, coverageType);
        String expectedResult = steps.isEmpty() ? "满足需求验收标准" : steps.getLast().expectedResult();
        return new TestDesignCandidate(
                id,
                task.id(),
                task.projectId(),
                requirement.id(),
                null,
                title,
                description,
                coverageType,
                priorityFor(requirement.priority(), coverageType),
                TestDesignCandidateStatus.GENERATED.name(),
                preconditions(requirement),
                stepsJson(steps),
                expectedResult,
                tagsText(List.of("wp5", "ai-generated", coverageType.toLowerCase(Locale.ROOT))),
                duplicateKey(requirement.id(), coverageType, title),
                confidenceFor(coverageType),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now
        );
    }

    private List<TestDesignStepResponse> templateSteps(RequirementResponse requirement, String coverageType) {
        String requirementTitle = redactSensitiveText(requirement.title());
        String criteria = StringUtils.hasText(requirement.acceptanceCriteria())
                ? redactSensitiveText(requirement.acceptanceCriteria())
                : "需求验收标准被满足";
        return switch (coverageType) {
            case "SMOKE" -> List.of(
                    step(0, "准备满足前置条件的基础测试数据", "测试数据可用于执行核心流程"),
                    step(1, "执行需求「" + requirementTitle + "」的核心操作", "核心操作完成且无阻断错误"),
                    step(2, "检查关键结果和页面/接口反馈", criteria)
            );
            case "EXCEPTION" -> List.of(
                    step(0, "准备缺失或非法的输入数据", "系统接受测试输入并进入校验逻辑"),
                    step(1, "执行需求「" + requirementTitle + "」的异常路径", "系统阻断非法操作"),
                    step(2, "检查错误提示、状态和审计记录", "错误提示清晰且未产生脏数据")
            );
            case "BOUNDARY" -> List.of(
                    step(0, "准备最小值、最大值和临界值数据", "边界数据准备完成"),
                    step(1, "分别提交边界值并观察处理结果", "有效边界通过，无效边界被拒绝"),
                    step(2, "核对结果持久化和提示信息", criteria)
            );
            case "PERMISSION" -> List.of(
                    step(0, "准备有权限与无权限两个账号", "账号权限边界清晰"),
                    step(1, "分别访问需求「" + requirementTitle + "」相关功能", "有权限账号可操作，无权限账号被拒绝"),
                    step(2, "核对权限失败响应和审计结果", "权限拒绝返回 403 或业务阻断信息")
            );
            case "REGRESSION" -> List.of(
                    step(0, "准备历史通过版本的关键输入", "回归基线数据可用"),
                    step(1, "执行需求「" + requirementTitle + "」历史主路径", "历史主路径仍然通过"),
                    step(2, "核对关联需求、接口或页面未出现回归", criteria)
            );
            default -> List.of(
                    step(0, "确认需求前置条件和测试数据", "前置条件满足"),
                    step(1, "执行需求「" + requirementTitle + "」主流程", "主流程完成"),
                    step(2, "核对验收标准", criteria)
            );
        };
    }

    private static TestDesignTask withModelInvocation(TestDesignTask task, ModelInvocationResult response) {
        return new TestDesignTask(
                task.id(), task.projectId(), task.title(), task.status(), task.requirementIds(), task.coverageTypes(),
                task.promptKey(), task.promptVersion(), response.invocationId(), response.providerName(),
                response.modelName(), task.totalRequirements(), task.generatedCount(), task.confirmedCount(),
                task.publishedCount(), task.errorMessage(), task.requestedBy(), task.idempotencyKey(),
                task.requestDigest(), task.inputDigest(), task.contextSummaryJson(), task.createdAt(), Instant.now()
        );
    }

    record GenerationAttempt(
            TestDesignTask task,
            List<TestDesignCandidate> candidates,
            String warningMessage
    ) {
    }

    private String stepsJson(List<TestDesignStepResponse> steps) {
        return responseMapper.stepsJson(steps);
    }

    private static TestDesignStepResponse step(int order, String action, String expectedResult) {
        return new TestDesignStepResponse(order, action, expectedResult);
    }

    private static String normalizeCoverageType(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return fallback;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CoverageType.codes().contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的覆盖类型: " + rawValue);
        }
        return normalized;
    }

    private static String normalizePriority(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return StringUtils.hasText(fallback) ? fallback : "MEDIUM";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CANDIDATE_PRIORITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的优先级: " + rawValue);
        }
        return normalized;
    }

    private static String priorityFor(String requirementPriority, String coverageType) {
        if ("EXCEPTION".equals(coverageType) || "PERMISSION".equals(coverageType)) {
            return "HIGH";
        }
        return normalizePriority(requirementPriority, "MEDIUM");
    }

    private static double confidenceFor(String coverageType) {
        return switch (coverageType) {
            case "SMOKE", "FUNCTIONAL" -> 0.86D;
            case "EXCEPTION" -> 0.82D;
            default -> 0.78D;
        };
    }

    private static String preconditions(RequirementResponse requirement) {
        if (StringUtils.hasText(requirement.acceptanceCriteria())) {
            return "需求验收标准已明确，测试前需准备满足业务上下文的数据";
        }
        return "需求描述已确认，测试数据和账号权限已准备";
    }

    private static String redactSensitiveText(String value) {
        // WP5 must not echo obvious secrets from WP3/WP4 source text while the full WP2 context packer is still pending.
        return TestDesignSensitiveText.redact(value);
    }

    private static String redactedPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = redactSensitiveText(value).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static List<String> summaryTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.replace('，', ',').split(",")).stream()
                .map(tag -> redactedPreview(tag, 64))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    private static String tagsText(List<String> tags) {
        if (tags == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                result.add(tag.trim());
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    private static boolean sameProject(String actualProjectId, String expectedProjectId) {
        return StringUtils.hasText(actualProjectId) && actualProjectId.equals(expectedProjectId);
    }

    private int maxCasesPerRequirement() {
        return properties.maxCasesPerRequirement() <= 0 ? 3 : properties.maxCasesPerRequirement();
    }

    private int contextLinkedAssetsPerRequirement() {
        return properties.effectiveContextLinkedAssetsPerRequirement();
    }

    private int contextExplicitAssetsPerType() {
        return properties.effectiveContextExplicitAssetsPerType();
    }

    private int contextExistingCasesPerRequirement() {
        return properties.effectiveContextExistingCasesPerRequirement();
    }

    private int contextRequirementDescriptionChars() {
        return properties.effectiveContextRequirementDescriptionChars();
    }

    private int contextAcceptanceCriteriaChars() {
        return properties.effectiveContextAcceptanceCriteriaChars();
    }

    private int contextAssetSchemaChars() {
        return properties.effectiveContextAssetSchemaChars();
    }

    private String normalizedGenerationMode() {
        if (!StringUtils.hasText(properties.generationMode())) {
            return GENERATION_MODE_RULE_TEMPLATE;
        }
        String normalized = properties.generationMode().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("MODEL_FALLBACK".equals(normalized)) {
            return GENERATION_MODE_MODEL_WITH_FALLBACK;
        }
        if (GENERATION_MODE_MODEL.equals(normalized) && properties.modelFallbackEnabled()) {
            return GENERATION_MODE_MODEL_WITH_FALLBACK;
        }
        return normalized;
    }

    private static String modelFallbackWarning(RuntimeException exception) {
        String message = "模型生成失败，已降级规则模板: " + safeErrorMessage(exception);
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }

    static String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        String redacted = redactSensitiveText(message).replaceAll("\\s+", " ").trim();
        return redacted.length() <= 500 ? redacted : redacted.substring(0, 497) + "...";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static final class TestDesignModelGenerationException extends RuntimeException {

        private final ModelInvocationResult response;

        private TestDesignModelGenerationException(ModelInvocationResult response, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.response = response;
        }

        private ModelInvocationResult response() {
            return response;
        }
    }
}
