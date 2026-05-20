package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.CreateApiRequest;
import com.songhg.veri.agent.asset.api.request.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.CreateLinkRequest;
import com.songhg.veri.agent.asset.api.request.CreatePageRequest;
import com.songhg.veri.agent.asset.api.request.CreateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.TraceLinkListRequest;
import com.songhg.veri.agent.asset.api.request.UpdateApiRequest;
import com.songhg.veri.agent.asset.api.request.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.UpdatePageRequest;
import com.songhg.veri.agent.asset.api.request.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.api.response.ApiResponseDTO;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final Set<String> REVIEW_STATUSES = Set.of("DRAFT", "REVIEWING", "APPROVED", "DEPRECATED");
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> REQUIREMENT_SOURCES = Set.of("IMPORT", "MANUAL");
    private static final Set<String> API_STATUSES = Set.of("ACTIVE", "DEPRECATED", "REMOVED");
    private static final Set<String> PAGE_STATUSES = Set.of("ACTIVE", "DEPRECATED");
    private static final Set<String> PAGE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE", "MANUAL");
    private static final Set<String> FLOW_STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");
    private static final Map<String, Set<String>> REVIEW_STATUS_TRANSITIONS = Map.of(
            "DRAFT", Set.of("DRAFT", "REVIEWING", "APPROVED", "DEPRECATED"),
            "REVIEWING", Set.of("REVIEWING", "DRAFT", "APPROVED", "DEPRECATED"),
            "APPROVED", Set.of("APPROVED", "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED")
    );
    private static final Map<String, Set<String>> API_STATUS_TRANSITIONS = Map.of(
            "ACTIVE", Set.of("ACTIVE", "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED", "REMOVED"),
            "REMOVED", Set.of("REMOVED")
    );
    private static final Map<String, Set<String>> PAGE_STATUS_TRANSITIONS = Map.of(
            "ACTIVE", Set.of("ACTIVE", "DEPRECATED"),
            "DEPRECATED", Set.of("DEPRECATED")
    );
    private static final Map<String, Set<String>> FLOW_STATUS_TRANSITIONS = Map.of(
            "DRAFT", Set.of("DRAFT", "ACTIVE", "ARCHIVED"),
            "ACTIVE", Set.of("ACTIVE", "ARCHIVED"),
            "ARCHIVED", Set.of("ARCHIVED")
    );
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final AssetRepository repository;
    private final PlatformContextClient contextClient;

    public AssetService(AssetRepository repository, PlatformContextClient contextClient) {
        this.repository = repository;
        this.contextClient = contextClient;
    }

    // ---- Requirements ----

    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        List<RequirementResponse> filtered = repository.requirements(trimToNull(request.getProjectId())).stream()
                .filter(value -> matches(value.status(), request.getStatus()))
                .filter(value -> matches(value.source(), request.getSource()))
                .filter(value -> containsKeyword(request.getKeyword(), value.code(), value.title(), value.description(), value.sourceRef(), value.tags()))
                .map(AssetService::toRequirementResponse)
                .sorted(Comparator.comparing(RequirementResponse::createdAt).reversed())
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public RequirementResponse getRequirement(UUID id) {
        return repository.requirement(id)
                .map(AssetService::toRequirementResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
    }

    public Optional<RequirementResponse> findImportedRequirement(String projectId, String sourceRef) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(sourceRef)) {
            return Optional.empty();
        }
        validateProjectWhenProvided(projectId);
        return repository.requirementBySourceRef(projectId, "IMPORT", sourceRef.trim())
                .map(AssetService::toRequirementResponse);
    }

    @Transactional
    public RequirementResponse createRequirement(CreateRequirementRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String source = valueIn(request.source(), "MANUAL", REQUIREMENT_SOURCES, "source");
        String sourceRef = trimToNull(request.sourceRef());
        if ("IMPORT".equals(source) && sourceRef != null) {
            Optional<AssetRequirement> existing = repository.requirementBySourceRef(request.projectId(), source, sourceRef);
            if (existing.isPresent()) {
                AssetRequirement merged = mergeImportedRequirement(existing.get(), request, now);
                if (sameRequirement(existing.get(), merged)) {
                    return toRequirementResponse(existing.get());
                }
                if (!"DRAFT".equals(existing.get().status())) {
                    writeProjectAudit("UPSERT_DENIED", "REQUIREMENT", existing.get().id(), scopeId, "DENIED");
                    throw new BusinessException(
                            ErrorCode.INVALID_STATE,
                            "既有导入需求已进入评审或审批状态，需人工处理差异后再更新"
                    );
                }
                writeProjectAudit("UPSERT", "REQUIREMENT", merged.id(), scopeId);
                VersionDiff diff = requirementDiff(existing.get(), merged);
                AssetRequirement stored = repository.saveRequirement(merged);
                saveRequirementHistory(stored, "UPSERT", diff);
                log.info("Updated imported requirement id={}, sourceRef={}, trace_id={}",
                        stored.id(), sourceRef, TraceContext.getTraceId());
                return toRequirementResponse(stored);
            }
        }
        AssetRequirement req = new AssetRequirement(
                id,
                assetCode("REQ", id),
                request.title(),
                request.description(),
                source,
                sourceRef,
                trimToNull(request.sourceUrl()),
                trimToNull(request.acceptanceCriteria()),
                initialStatus(request.status(), "DRAFT", "REQUIREMENT"),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                request.tags(),
                1,
                now,
                now
        );
        writeProjectAudit("CREATE", "REQUIREMENT", id, scopeId);
        AssetRequirement stored = repository.saveRequirement(req);
        if (stored.id().equals(id)) {
            saveRequirementHistory(stored, "CREATE", VersionDiff.empty());
            log.info("Created requirement id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        }
        return toRequirementResponse(stored);
    }

    private AssetRequirement mergeImportedRequirement(
            AssetRequirement existing,
            CreateRequirementRequest request,
            Instant now
    ) {
        return new AssetRequirement(
                existing.id(),
                existing.code(),
                request.title(),
                trimToNull(request.description()),
                existing.source(),
                existing.sourceRef(),
                trimToNull(request.sourceUrl()),
                trimToNull(request.acceptanceCriteria()),
                existing.status(),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                mergeTags(existing.tags(), request.tags()),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
    }

    private static boolean sameRequirement(AssetRequirement left, AssetRequirement right) {
        return java.util.Objects.equals(left.title(), right.title())
                && java.util.Objects.equals(left.description(), right.description())
                && java.util.Objects.equals(left.sourceUrl(), right.sourceUrl())
                && java.util.Objects.equals(left.acceptanceCriteria(), right.acceptanceCriteria())
                && java.util.Objects.equals(left.status(), right.status())
                && java.util.Objects.equals(left.priority(), right.priority())
                && java.util.Objects.equals(normalizedTags(left.tags()), normalizedTags(right.tags()));
    }

    @Transactional
    public RequirementResponse updateRequirement(UUID id, UpdateRequirementRequest request) {
        AssetRequirement existing = repository.requirement(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "REQUIREMENT",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                REVIEW_STATUSES,
                REVIEW_STATUS_TRANSITIONS
        );
        AssetRequirement updated = new AssetRequirement(
                id,
                existing.code(),
                request.title(),
                request.description(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceUrl(),
                existing.acceptanceCriteria(),
                nextStatus,
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                request.tags(),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        saveRequirementHistory(stored, "UPDATE", requirementDiff(existing, stored));
        return toRequirementResponse(stored);
    }

    public List<AssetVersionHistoryResponse> requirementVersions(UUID id) {
        AssetRequirement requirement = repository.requirement(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        return repository.assetVersionHistory("REQUIREMENT", requirement.id()).stream()
                .map(AssetService::toVersionHistoryResponse)
                .toList();
    }

    // ---- APIs ----

    public com.songhg.veri.agent.common.api.PageResponse<ApiResponseDTO> listApis(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        List<ApiResponseDTO> filtered = repository.apis(trimToNull(request.getProjectId())).stream()
                .filter(value -> matches(value.status(), request.getStatus()))
                .filter(value -> matches(value.source(), request.getSource()))
                .filter(value -> containsKeyword(request.getKeyword(), value.code(), value.summary(), value.description(), value.path(), value.sourceRef()))
                .map(AssetService::toApiResponse)
                .sorted(Comparator.comparing(ApiResponseDTO::createdAt).reversed())
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public ApiResponseDTO getApi(UUID id) {
        return repository.api(id)
                .map(AssetService::toApiResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
    }

    public ApiResponseDTO createApi(CreateApiRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetApi api = new AssetApi(
                id,
                assetCode("API", id),
                request.summary(),
                request.description(),
                request.httpMethod(),
                request.path(),
                "MANUAL",
                null,
                request.requestSchema(),
                request.responseSchema(),
                request.projectId(),
                initialStatus(request.status(), "ACTIVE", "API"),
                now,
                now
        );
        writeProjectAudit("CREATE", "API", id, scopeId);
        repository.saveApi(api);
        log.info("Created api id={}, summary={}, trace_id={}", id, request.summary(), TraceContext.getTraceId());
        return toApiResponse(api);
    }

    public ApiResponseDTO updateApi(UUID id, UpdateApiRequest request) {
        AssetApi existing = repository.api(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "API",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                API_STATUSES,
                API_STATUS_TRANSITIONS
        );
        AssetApi updated = new AssetApi(
                id,
                existing.code(),
                request.summary(),
                request.description(),
                request.httpMethod(),
                request.path(),
                existing.source(),
                existing.sourceRef(),
                request.requestSchema(),
                request.responseSchema(),
                existing.projectId(),
                nextStatus,
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "API", id, existing.projectId());
        repository.saveApi(updated);
        return toApiResponse(updated);
    }

    // ---- Pages ----

    public com.songhg.veri.agent.common.api.PageResponse<PageResponse> listPages(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        List<PageResponse> filtered = repository.pages(trimToNull(request.getProjectId())).stream()
                .filter(value -> matches(value.status(), request.getStatus()))
                .filter(value -> matches(value.source(), request.getSource()))
                .filter(value -> containsKeyword(request.getKeyword(), value.code(), value.name(), value.urlPattern(), value.sourceRef()))
                .map(AssetService::toPageResponse)
                .sorted(Comparator.comparing(PageResponse::createdAt).reversed())
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public PageResponse getPage(UUID id) {
        return repository.page(id)
                .map(AssetService::toPageResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
    }

    public PageResponse createPage(CreatePageRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetPage page = new AssetPage(
                id,
                assetCode("PAGE", id),
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), "MANUAL", PAGE_SOURCES, "source"),
                request.sourceRef(),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                request.projectId(),
                initialStatus(request.status(), "ACTIVE", "PAGE"),
                now,
                now
        );
        writeProjectAudit("CREATE", "PAGE", id, scopeId);
        repository.savePage(page);
        log.info("Created page id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return toPageResponse(page);
    }

    public PageResponse updatePage(UUID id, UpdatePageRequest request) {
        AssetPage existing = repository.page(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "PAGE",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                PAGE_STATUSES,
                PAGE_STATUS_TRANSITIONS
        );
        AssetPage updated = new AssetPage(
                id,
                existing.code(),
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), existing.source(), PAGE_SOURCES, "source"),
                request.sourceRef(),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                existing.projectId(),
                nextStatus,
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "PAGE", id, existing.projectId());
        repository.savePage(updated);
        return toPageResponse(updated);
    }

    // ---- Business Flows ----

    public com.songhg.veri.agent.common.api.PageResponse<BusinessFlowResponse> listBusinessFlows(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        List<BusinessFlowResponse> filtered = repository.businessFlows(trimToNull(request.getProjectId())).stream()
                .filter(value -> matches(value.status(), request.getStatus()))
                .filter(value -> containsKeyword(request.getKeyword(), value.code(), value.name(), value.description()))
                .map(AssetService::toBusinessFlowResponse)
                .sorted(Comparator.comparing(BusinessFlowResponse::createdAt).reversed())
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public BusinessFlowResponse getBusinessFlow(UUID id) {
        return repository.businessFlow(id)
                .map(AssetService::toBusinessFlowResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
    }

    public BusinessFlowResponse createBusinessFlow(CreateBusinessFlowRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetBusinessFlow flow = new AssetBusinessFlow(
                id,
                assetCode("FLOW", id),
                request.name(),
                request.description(),
                jsonValue(request.flowJson()),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                initialStatus(request.status(), "DRAFT", "BUSINESS_FLOW"),
                now,
                now
        );
        writeProjectAudit("CREATE", "BUSINESS_FLOW", id, scopeId);
        repository.saveBusinessFlow(flow);
        log.info("Created business flow id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return toBusinessFlowResponse(flow);
    }

    public BusinessFlowResponse updateBusinessFlow(UUID id, UpdateBusinessFlowRequest request) {
        AssetBusinessFlow existing = repository.businessFlow(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "BUSINESS_FLOW",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                FLOW_STATUSES,
                FLOW_STATUS_TRANSITIONS
        );
        AssetBusinessFlow updated = new AssetBusinessFlow(
                id,
                existing.code(),
                request.name(),
                request.description(),
                jsonValue(request.flowJson()),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                nextStatus,
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "BUSINESS_FLOW", id, existing.projectId());
        repository.saveBusinessFlow(updated);
        return toBusinessFlowResponse(updated);
    }

    // ---- Test Cases ----

    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(AssetListRequest request) {
        validateProjectWhenProvided(request.getProjectId());
        List<TestCaseResponse> filtered = repository.testCases(trimToNull(request.getProjectId())).stream()
                .filter(value -> matches(value.status(), request.getStatus()))
                .filter(value -> matches(value.source(), request.getSource()))
                .filter(value -> containsKeyword(request.getKeyword(), value.code(), value.title(), value.description(), value.sourceRef(), value.tags()))
                .map(tc -> toTestCaseResponse(tc, tc.steps()))
                .sorted(Comparator.comparing(TestCaseResponse::createdAt).reversed())
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public TestCaseResponse getTestCase(UUID id) {
        TestCaseRecord testCase = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return toTestCaseResponse(testCase, testCase.steps());
    }

    @Transactional
    public TestCaseResponse createTestCase(CreateTestCaseRequest request) {
        String scopeId = projectContext(request.projectId()).projectId();
        validateRequirementBelongsToProject(request.requirementId(), request.projectId());
        validateApiBelongsToProject(request.apiId(), request.projectId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        List<CreateTestCaseRequest.StepDto> requestedSteps = Optional.ofNullable(request.steps())
                .orElse(Collections.emptyList());
        List<TestCaseStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < requestedSteps.size(); i++) {
            CreateTestCaseRequest.StepDto step = requestedSteps.get(i);
            steps.add(new TestCaseStep(UUID.randomUUID(), id, i, step.action(), step.expectedResult()));
        }
        TestCaseRecord tc = new TestCaseRecord(
                id,
                assetCode("TC", id),
                request.title(),
                request.description(),
                request.projectId(),
                request.requirementId(),
                request.apiId(),
                "MANUAL",
                null,
                initialStatus(request.status(), "DRAFT", "TEST_CASE"),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.tags(),
                steps,
                1,
                now,
                now
        );
        writeProjectAudit("CREATE", "TEST_CASE", id, scopeId);
        TestCaseRecord stored = repository.saveTestCase(tc);
        saveTestCaseHistory(stored, "CREATE", VersionDiff.empty());
        log.info("Created test case id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        return toTestCaseResponse(stored, steps);
    }

    @Transactional
    public TestCaseResponse updateTestCase(UUID id, UpdateTestCaseRequest request) {
        TestCaseRecord existing = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        validateRequirementBelongsToProject(request.requirementId(), existing.projectId());
        validateApiBelongsToProject(request.apiId(), existing.projectId());
        List<TestCaseStep> existingSteps = existing.steps();
        Instant now = Instant.now();
        String nextStatus = nextStatus(
                "TEST_CASE",
                id,
                existing.projectId(),
                existing.status(),
                request.status(),
                REVIEW_STATUSES,
                REVIEW_STATUS_TRANSITIONS
        );
        TestCaseRecord updated = new TestCaseRecord(
                id,
                existing.code(),
                request.title(),
                request.description(),
                existing.projectId(),
                request.requirementId(),
                request.apiId(),
                existing.source(),
                existing.sourceRef(),
                nextStatus,
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                request.tags(),
                existingSteps,
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        writeProjectAudit("UPDATE", "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        saveTestCaseHistory(stored, "UPDATE", testCaseDiff(existing, stored));
        return toTestCaseResponse(stored, existingSteps);
    }

    public List<AssetVersionHistoryResponse> testCaseVersions(UUID id) {
        TestCaseRecord testCase = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return repository.assetVersionHistory("TEST_CASE", testCase.id()).stream()
                .map(AssetService::toVersionHistoryResponse)
                .toList();
    }

    // ---- Test Case Steps ----

    public List<TestCaseStepResponse> listTestCaseSteps(UUID caseId) {
        getTestCase(caseId);
        List<TestCaseStep> steps = repository.testCaseSteps(caseId);
        return steps.stream()
                .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                .toList();
    }

    @Transactional
    public List<TestCaseStepResponse> updateTestCaseSteps(UUID caseId, UpdateTestCaseStepsRequest request) {
        TestCaseRecord existing = repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        List<TestCaseStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            UpdateTestCaseStepsRequest.StepItem item = request.steps().get(i);
            steps.add(new TestCaseStep(UUID.randomUUID(), caseId, i, item.action(), item.expectedResult()));
        }
        writeProjectAudit("UPDATE", "TEST_CASE_STEPS", caseId, existing.projectId());
        Instant now = Instant.now();
        TestCaseRecord updated = new TestCaseRecord(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.projectId(),
                existing.requirementId(),
                existing.apiId(),
                existing.source(),
                existing.sourceRef(),
                existing.status(),
                existing.priority(),
                existing.tags(),
                steps,
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        TestCaseRecord stored = repository.saveTestCase(updated);
        saveTestCaseHistory(stored, "STEPS_UPDATE", testCaseDiff(existing, stored));
        return steps.stream()
                .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                .toList();
    }

    // ---- Trace Links ----

    public com.songhg.veri.agent.common.api.PageResponse<TraceLinkResponse> listLinks(TraceLinkListRequest request) {
        List<TraceLinkResponse> filtered = repository.traceLinks(request.getRequirementId(), request.getApiId(), request.getCaseId()).stream()
                .map(AssetService::toTraceLinkResponse)
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public TraceLinkResponse createLink(CreateLinkRequest request) {
        AssetRequirement requirement = repository.requirement(request.requirementId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + request.requirementId()));
        validateApiBelongsToProject(request.apiId(), requirement.projectId());
        validateTestCaseBelongsToProject(request.caseId(), requirement.projectId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TraceLink link = new TraceLink(id, request.requirementId(), request.apiId(), request.caseId(), now);
        writeProjectAudit("CREATE", "TRACE_LINK", id, requirement.projectId());
        repository.saveTraceLink(link);
        log.info("Created trace link id={}, requirementId={}, trace_id={}",
                id, request.requirementId(), TraceContext.getTraceId());
        return toTraceLinkResponse(link);
    }

    // ---- Health ----

    public String health() {
        return "UP";
    }

    // ---- Mappers ----

    private static RequirementResponse toRequirementResponse(AssetRequirement r) {
        return new RequirementResponse(
                r.id(), r.code(), r.title(), r.description(), r.source(), r.sourceRef(), r.sourceUrl(), r.acceptanceCriteria(),
                r.status(), r.priority(),
                r.projectId(), r.tags(), r.version(), r.createdAt(), r.updatedAt()
        );
    }

    private static ApiResponseDTO toApiResponse(AssetApi a) {
        return new ApiResponseDTO(
                a.id(), a.code(), a.summary(), a.description(), a.httpMethod(), a.path(), a.source(), a.sourceRef(),
                a.requestSchema(), a.responseSchema(), a.projectId(),
                a.status(), a.createdAt(), a.updatedAt()
        );
    }

    private static PageResponse toPageResponse(AssetPage p) {
        return new PageResponse(
                p.id(), p.code(), p.name(), p.urlPattern(), p.source(), p.sourceRef(), p.componentTree(), p.screenshotUrl(),
                p.projectId(), p.status(), p.createdAt(), p.updatedAt()
        );
    }

    private static BusinessFlowResponse toBusinessFlowResponse(AssetBusinessFlow f) {
        return new BusinessFlowResponse(
                f.id(), f.code(), f.name(), f.description(), f.flowJson(), f.priority(),
                f.projectId(), f.status(), f.createdAt(), f.updatedAt()
        );
    }

    private static TestCaseResponse toTestCaseResponse(TestCaseRecord tc, List<TestCaseStep> steps) {
        List<TestCaseStepResponse> stepResponses = steps == null ? Collections.emptyList()
                : steps.stream()
                        .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                        .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                        .toList();
        return new TestCaseResponse(
                tc.id(), tc.code(), tc.title(), tc.description(), tc.requirementId(), tc.apiId(),
                tc.source(), tc.sourceRef(),
                tc.status(), tc.priority(), tc.tags(), stepResponses,
                tc.version(), tc.createdAt(), tc.updatedAt()
        );
    }

    private static AssetVersionHistoryResponse toVersionHistoryResponse(AssetVersionHistory history) {
        return new AssetVersionHistoryResponse(
                history.id(),
                history.assetType(),
                history.assetId(),
                history.projectId(),
                history.version(),
                history.changeType(),
                history.actor(),
                splitChangedFields(history.changedFields()),
                jsonNode(history.diffJson()),
                jsonNode(history.snapshotJson()),
                history.traceId(),
                history.createdAt()
        );
    }

    private static TraceLinkResponse toTraceLinkResponse(TraceLink l) {
        return new TraceLinkResponse(l.id(), l.requirementId(), l.apiId(), l.caseId(), l.createdAt());
    }

    private void saveRequirementHistory(AssetRequirement requirement, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "REQUIREMENT",
                requirement.id(),
                requirement.projectId(),
                requirement.version(),
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(requirementSnapshot(requirement)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private void saveTestCaseHistory(TestCaseRecord testCase, String changeType, VersionDiff diff) {
        repository.saveVersionHistory(new AssetVersionHistory(
                UUID.randomUUID(),
                "TEST_CASE",
                testCase.id(),
                testCase.projectId(),
                testCase.version(),
                changeType,
                currentActor(),
                String.join(",", diff.changedFields()),
                diff.diffJson(),
                jsonString(testCaseSnapshot(testCase)),
                TraceContext.getTraceId(),
                Instant.now()
        ));
    }

    private static VersionDiff requirementDiff(AssetRequirement before, AssetRequirement after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "title", before.title(), after.title());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "sourceUrl", before.sourceUrl(), after.sourceUrl());
        addDiff(diff, "acceptanceCriteria", before.acceptanceCriteria(), after.acceptanceCriteria());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "tags", normalizedTags(before.tags()), normalizedTags(after.tags()));
        return VersionDiff.of(diff);
    }

    private static VersionDiff testCaseDiff(TestCaseRecord before, TestCaseRecord after) {
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        addDiff(diff, "title", before.title(), after.title());
        addDiff(diff, "description", before.description(), after.description());
        addDiff(diff, "requirementId", before.requirementId(), after.requirementId());
        addDiff(diff, "apiId", before.apiId(), after.apiId());
        addDiff(diff, "status", before.status(), after.status());
        addDiff(diff, "priority", before.priority(), after.priority());
        addDiff(diff, "tags", normalizedTags(before.tags()), normalizedTags(after.tags()));
        addDiff(diff, "steps", stepSnapshot(before.steps()), stepSnapshot(after.steps()));
        return VersionDiff.of(diff);
    }

    private static void addDiff(LinkedHashMap<String, Object> diff, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        LinkedHashMap<String, Object> fieldDiff = new LinkedHashMap<>();
        fieldDiff.put("before", before);
        fieldDiff.put("after", after);
        diff.put(field, fieldDiff);
    }

    private static LinkedHashMap<String, Object> requirementSnapshot(AssetRequirement requirement) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", requirement.id());
        snapshot.put("code", requirement.code());
        snapshot.put("title", requirement.title());
        snapshot.put("description", requirement.description());
        snapshot.put("source", requirement.source());
        snapshot.put("sourceRef", requirement.sourceRef());
        snapshot.put("sourceUrl", requirement.sourceUrl());
        snapshot.put("acceptanceCriteria", requirement.acceptanceCriteria());
        snapshot.put("status", requirement.status());
        snapshot.put("priority", requirement.priority());
        snapshot.put("projectId", requirement.projectId());
        snapshot.put("tags", requirement.tags());
        snapshot.put("version", requirement.version());
        snapshot.put("createdAt", requirement.createdAt());
        snapshot.put("updatedAt", requirement.updatedAt());
        return snapshot;
    }

    private static LinkedHashMap<String, Object> testCaseSnapshot(TestCaseRecord testCase) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", testCase.id());
        snapshot.put("code", testCase.code());
        snapshot.put("title", testCase.title());
        snapshot.put("description", testCase.description());
        snapshot.put("projectId", testCase.projectId());
        snapshot.put("requirementId", testCase.requirementId());
        snapshot.put("apiId", testCase.apiId());
        snapshot.put("source", testCase.source());
        snapshot.put("sourceRef", testCase.sourceRef());
        snapshot.put("status", testCase.status());
        snapshot.put("priority", testCase.priority());
        snapshot.put("tags", testCase.tags());
        snapshot.put("steps", stepSnapshot(testCase.steps()));
        snapshot.put("version", testCase.version());
        snapshot.put("createdAt", testCase.createdAt());
        snapshot.put("updatedAt", testCase.updatedAt());
        return snapshot;
    }

    private static List<Map<String, Object>> stepSnapshot(List<TestCaseStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                .map(step -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", step.stepOrder());
                    item.put("action", step.action());
                    item.put("expectedResult", step.expectedResult());
                    return item;
                })
                .toList();
    }

    private static String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ServicePrincipal servicePrincipal) {
            return servicePrincipal.callerService() + ":" + servicePrincipal.delegatedUserId();
        }
        if (principal instanceof AuthUserPrincipal userPrincipal) {
            return StringUtils.hasText(userPrincipal.username())
                    ? userPrincipal.username()
                    : userPrincipal.userId().toString();
        }
        if (principal instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return "system";
    }

    private static List<String> splitChangedFields(String changedFields) {
        if (!StringUtils.hasText(changedFields)) {
            return List.of();
        }
        return Arrays.stream(changedFields.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static JsonNode jsonNode(String json) {
        if (!StringUtils.hasText(json)) {
            return JSON_MAPPER.createObjectNode();
        }
        try {
            return JSON_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return JSON_MAPPER.createObjectNode();
        }
    }

    private static String jsonString(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产版本历史序列化失败");
        }
    }

    private record VersionDiff(List<String> changedFields, String diffJson) {
        private static VersionDiff empty() {
            return new VersionDiff(List.of(), "{}");
        }

        private static VersionDiff of(LinkedHashMap<String, Object> diff) {
            if (diff.isEmpty()) {
                return empty();
            }
            return new VersionDiff(new ArrayList<>(diff.keySet()), jsonString(diff));
        }
    }

    private void validateProjectWhenProvided(String projectId) {
        if (StringUtils.hasText(projectId)) {
            projectContext(projectId);
        }
    }

    private PlatformContextClient.ProjectContext projectContext(String projectId) {
        PlatformContextClient.ProjectContext context = contextClient.getProjectContext(projectId);
        if (!StringUtils.hasText(context.projectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目上下文不存在: " + projectId);
        }
        if (!"ACTIVE".equals(context.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许写入资产: " + projectId);
        }
        return context;
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId) {
        writeProjectAudit(action, resourceType, resourceId, projectId, "SUCCEEDED");
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId, String result) {
        String scopeId = StringUtils.hasText(projectId) ? projectContext(projectId).projectId() : null;
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), scopeId, result);
    }

    private void validateRequirementBelongsToProject(UUID requirementId, String projectId) {
        if (requirementId == null) {
            return;
        }
        AssetRequirement requirement = repository.requirement(requirementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + requirementId));
        ensureSameProject("需求", requirement.id(), requirement.projectId(), projectId);
    }

    private void validateApiBelongsToProject(UUID apiId, String projectId) {
        if (apiId == null) {
            return;
        }
        AssetApi api = repository.api(apiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + apiId));
        ensureSameProject("API", api.id(), api.projectId(), projectId);
    }

    private void validateTestCaseBelongsToProject(UUID caseId, String projectId) {
        if (caseId == null) {
            return;
        }
        TestCaseRecord testCase = repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        ensureSameProject("测试用例", testCase.id(), testCase.projectId(), projectId);
    }

    private void ensureSameProject(String resourceName, UUID resourceId, String actualProjectId, String expectedProjectId) {
        if (!StringUtils.hasText(actualProjectId) || !actualProjectId.equals(expectedProjectId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    resourceName + "不属于当前项目: " + resourceId
            );
        }
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String initialStatus(String rawValue, String defaultValue, String resourceType) {
        return switch (resourceType) {
            case "API" -> valueIn(rawValue, defaultValue, API_STATUSES, "status");
            case "PAGE" -> valueIn(rawValue, defaultValue, PAGE_STATUSES, "status");
            case "BUSINESS_FLOW" -> valueIn(rawValue, defaultValue, FLOW_STATUSES, "status");
            default -> valueIn(rawValue, defaultValue, REVIEW_STATUSES, "status");
        };
    }

    private String nextStatus(
            String resourceType,
            UUID resourceId,
            String projectId,
            String currentStatus,
            String rawNextStatus,
            Set<String> allowedStatuses,
            Map<String, Set<String>> transitions
    ) {
        String nextStatus = valueIn(rawNextStatus, currentStatus, allowedStatuses, "status");
        if (!transitions.getOrDefault(currentStatus, Set.of(currentStatus)).contains(nextStatus)) {
            writeProjectAudit("STATUS_CHANGE_DENIED", resourceType, resourceId, projectId, "DENIED");
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    resourceType + " 状态不允许从 " + currentStatus + " 变更为 " + nextStatus
            );
        }
        return nextStatus;
    }

    private static <T> com.songhg.veri.agent.common.api.PageResponse<T> page(List<T> items, int index, int size) {
        int from = Math.min(index * size, items.size());
        int to = Math.min(from + size, items.size());
        return com.songhg.veri.agent.common.api.PageResponse.of(items.subList(from, to), index, size, items.size());
    }

    private static boolean matches(String actual, String expected) {
        return !StringUtils.hasText(expected) || expected.trim().equalsIgnoreCase(actual);
    }

    private static boolean containsKeyword(String keyword, String... values) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String mergeTags(String existing, String incoming) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, existing);
        addTags(tags, incoming);
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private static String normalizedTags(String rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, rawTags);
        return String.join(",", tags);
    }

    private static void addTags(LinkedHashSet<String> tags, String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return;
        }
        for (String tag : rawTags.replace("，", ",").split(",")) {
            String trimmed = tag.trim();
            if (StringUtils.hasText(trimmed)) {
                tags.add(trimmed);
            }
        }
    }

    private static String jsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text : null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 字段格式不合法");
        }
    }
}
