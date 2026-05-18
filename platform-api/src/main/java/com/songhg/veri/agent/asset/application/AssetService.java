package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.CreateApiRequest;
import com.songhg.veri.agent.asset.api.request.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.CreateLinkRequest;
import com.songhg.veri.agent.asset.api.request.CreatePageRequest;
import com.songhg.veri.agent.asset.api.request.CreateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateApiRequest;
import com.songhg.veri.agent.asset.api.request.UpdateBusinessFlowRequest;
import com.songhg.veri.agent.asset.api.request.UpdatePageRequest;
import com.songhg.veri.agent.asset.api.request.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.api.response.ApiResponseDTO;
import com.songhg.veri.agent.asset.api.response.BusinessFlowResponse;
import com.songhg.veri.agent.asset.api.response.PageResponse;
import com.songhg.veri.agent.asset.api.response.RequirementResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final Set<String> REVIEW_STATUSES = Set.of("DRAFT", "REVIEWING", "APPROVED", "DEPRECATED");
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> API_STATUSES = Set.of("ACTIVE", "DEPRECATED", "REMOVED");
    private static final Set<String> PAGE_STATUSES = Set.of("ACTIVE", "DEPRECATED");
    private static final Set<String> PAGE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE", "MANUAL");
    private static final Set<String> FLOW_STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final AssetRepository repository;
    private final PlatformContextClient contextClient;

    public AssetService(AssetRepository repository, PlatformContextClient contextClient) {
        this.repository = repository;
        this.contextClient = contextClient;
    }

    // ---- Requirements ----

    public List<RequirementResponse> listRequirements(String projectId) {
        return repository.requirements(projectId).stream()
                .map(AssetService::toRequirementResponse)
                .sorted(Comparator.comparing(RequirementResponse::createdAt).reversed())
                .toList();
    }

    public RequirementResponse getRequirement(UUID id) {
        return repository.requirement(id)
                .map(AssetService::toRequirementResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
    }

    public RequirementResponse createRequirement(CreateRequirementRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetRequirement req = new AssetRequirement(
                id,
                request.title(),
                request.description(),
                valueIn(request.status(), "DRAFT", REVIEW_STATUSES, "status"),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                request.tags(),
                now,
                now
        );
        repository.saveRequirement(req);
        contextClient.writeAuditEvent("CREATE", "REQUIREMENT", id.toString(), "SUCCEEDED");
        log.info("Created requirement id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        return toRequirementResponse(req);
    }

    public RequirementResponse updateRequirement(UUID id, UpdateRequirementRequest request) {
        AssetRequirement existing = repository.requirement(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        Instant now = Instant.now();
        AssetRequirement updated = new AssetRequirement(
                id,
                request.title(),
                request.description(),
                valueIn(request.status(), existing.status(), REVIEW_STATUSES, "status"),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                request.tags(),
                existing.createdAt(),
                now
        );
        repository.saveRequirement(updated);
        contextClient.writeAuditEvent("UPDATE", "REQUIREMENT", id.toString(), "SUCCEEDED");
        return toRequirementResponse(updated);
    }

    // ---- APIs ----

    public List<ApiResponseDTO> listApis(String projectId) {
        return repository.apis(projectId).stream()
                .map(AssetService::toApiResponse)
                .sorted(Comparator.comparing(ApiResponseDTO::createdAt).reversed())
                .toList();
    }

    public ApiResponseDTO getApi(UUID id) {
        return repository.api(id)
                .map(AssetService::toApiResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
    }

    public ApiResponseDTO createApi(CreateApiRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetApi api = new AssetApi(
                id,
                request.summary(),
                request.description(),
                request.httpMethod(),
                request.path(),
                request.requestSchema(),
                request.responseSchema(),
                request.projectId(),
                valueIn(request.status(), "ACTIVE", API_STATUSES, "status"),
                now,
                now
        );
        repository.saveApi(api);
        contextClient.writeAuditEvent("CREATE", "API", id.toString(), "SUCCEEDED");
        log.info("Created api id={}, summary={}, trace_id={}", id, request.summary(), TraceContext.getTraceId());
        return toApiResponse(api);
    }

    public ApiResponseDTO updateApi(UUID id, UpdateApiRequest request) {
        AssetApi existing = repository.api(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        Instant now = Instant.now();
        AssetApi updated = new AssetApi(
                id,
                request.summary(),
                request.description(),
                request.httpMethod(),
                request.path(),
                request.requestSchema(),
                request.responseSchema(),
                existing.projectId(),
                valueIn(request.status(), existing.status(), API_STATUSES, "status"),
                existing.createdAt(),
                now
        );
        repository.saveApi(updated);
        contextClient.writeAuditEvent("UPDATE", "API", id.toString(), "SUCCEEDED");
        return toApiResponse(updated);
    }

    // ---- Pages ----

    public List<PageResponse> listPages(String projectId) {
        return repository.pages(projectId).stream()
                .map(AssetService::toPageResponse)
                .sorted(Comparator.comparing(PageResponse::createdAt).reversed())
                .toList();
    }

    public PageResponse getPage(UUID id) {
        return repository.page(id)
                .map(AssetService::toPageResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
    }

    public PageResponse createPage(CreatePageRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetPage page = new AssetPage(
                id,
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), "MANUAL", PAGE_SOURCES, "source"),
                request.sourceRef(),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                request.projectId(),
                valueIn(request.status(), "ACTIVE", PAGE_STATUSES, "status"),
                now,
                now
        );
        repository.savePage(page);
        contextClient.writeAuditEvent("CREATE", "PAGE", id.toString(), "SUCCEEDED");
        log.info("Created page id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return toPageResponse(page);
    }

    public PageResponse updatePage(UUID id, UpdatePageRequest request) {
        AssetPage existing = repository.page(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        Instant now = Instant.now();
        AssetPage updated = new AssetPage(
                id,
                request.name(),
                request.urlPattern(),
                valueIn(request.source(), existing.source(), PAGE_SOURCES, "source"),
                request.sourceRef(),
                jsonValue(request.componentTree()),
                request.screenshotUrl(),
                existing.projectId(),
                valueIn(request.status(), existing.status(), PAGE_STATUSES, "status"),
                existing.createdAt(),
                now
        );
        repository.savePage(updated);
        contextClient.writeAuditEvent("UPDATE", "PAGE", id.toString(), "SUCCEEDED");
        return toPageResponse(updated);
    }

    // ---- Business Flows ----

    public List<BusinessFlowResponse> listBusinessFlows(String projectId) {
        return repository.businessFlows(projectId).stream()
                .map(AssetService::toBusinessFlowResponse)
                .sorted(Comparator.comparing(BusinessFlowResponse::createdAt).reversed())
                .toList();
    }

    public BusinessFlowResponse getBusinessFlow(UUID id) {
        return repository.businessFlow(id)
                .map(AssetService::toBusinessFlowResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
    }

    public BusinessFlowResponse createBusinessFlow(CreateBusinessFlowRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetBusinessFlow flow = new AssetBusinessFlow(
                id,
                request.name(),
                request.description(),
                jsonValue(request.flowJson()),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                valueIn(request.status(), "DRAFT", FLOW_STATUSES, "status"),
                now,
                now
        );
        repository.saveBusinessFlow(flow);
        contextClient.writeAuditEvent("CREATE", "BUSINESS_FLOW", id.toString(), "SUCCEEDED");
        log.info("Created business flow id={}, name={}, trace_id={}", id, request.name(), TraceContext.getTraceId());
        return toBusinessFlowResponse(flow);
    }

    public BusinessFlowResponse updateBusinessFlow(UUID id, UpdateBusinessFlowRequest request) {
        AssetBusinessFlow existing = repository.businessFlow(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        Instant now = Instant.now();
        AssetBusinessFlow updated = new AssetBusinessFlow(
                id,
                request.name(),
                request.description(),
                jsonValue(request.flowJson()),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                valueIn(request.status(), existing.status(), FLOW_STATUSES, "status"),
                existing.createdAt(),
                now
        );
        repository.saveBusinessFlow(updated);
        contextClient.writeAuditEvent("UPDATE", "BUSINESS_FLOW", id.toString(), "SUCCEEDED");
        return toBusinessFlowResponse(updated);
    }

    // ---- Test Cases ----

    public List<TestCaseResponse> listTestCases(String projectId) {
        return repository.testCases(projectId).stream()
                .map(tc -> toTestCaseResponse(tc, tc.steps()))
                .sorted(Comparator.comparing(TestCaseResponse::createdAt).reversed())
                .toList();
    }

    public TestCaseResponse getTestCase(UUID id) {
        TestCaseRecord testCase = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return toTestCaseResponse(testCase, testCase.steps());
    }

    public TestCaseResponse createTestCase(CreateTestCaseRequest request) {
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
                request.title(),
                request.description(),
                request.projectId(),
                request.requirementId(),
                request.apiId(),
                valueIn(request.status(), "DRAFT", REVIEW_STATUSES, "status"),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.tags(),
                steps,
                now,
                now
        );
        repository.saveTestCase(tc);
        contextClient.writeAuditEvent("CREATE", "TEST_CASE", id.toString(), "SUCCEEDED");
        log.info("Created test case id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        return toTestCaseResponse(tc, steps);
    }

    public TestCaseResponse updateTestCase(UUID id, UpdateTestCaseRequest request) {
        TestCaseRecord existing = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        List<TestCaseStep> existingSteps = existing.steps();
        Instant now = Instant.now();
        TestCaseRecord updated = new TestCaseRecord(
                id,
                request.title(),
                request.description(),
                existing.projectId(),
                request.requirementId(),
                request.apiId(),
                valueIn(request.status(), existing.status(), REVIEW_STATUSES, "status"),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                request.tags(),
                existingSteps,
                existing.createdAt(),
                now
        );
        repository.saveTestCase(updated);
        contextClient.writeAuditEvent("UPDATE", "TEST_CASE", id.toString(), "SUCCEEDED");
        return toTestCaseResponse(updated, existingSteps);
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

    public List<TestCaseStepResponse> updateTestCaseSteps(UUID caseId, UpdateTestCaseStepsRequest request) {
        TestCaseRecord existing = repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        List<TestCaseStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            UpdateTestCaseStepsRequest.StepItem item = request.steps().get(i);
            steps.add(new TestCaseStep(UUID.randomUUID(), caseId, i, item.action(), item.expectedResult()));
        }
        repository.replaceTestCaseSteps(caseId, steps);
        contextClient.writeAuditEvent("UPDATE", "TEST_CASE_STEPS", caseId.toString(), "SUCCEEDED");
        return steps.stream()
                .map(s -> new TestCaseStepResponse(s.stepOrder(), s.action(), s.expectedResult()))
                .toList();
    }

    // ---- Trace Links ----

    public List<TraceLinkResponse> listLinks(UUID requirementId, UUID apiId, UUID caseId) {
        return repository.traceLinks(requirementId, apiId, caseId).stream()
                .map(AssetService::toTraceLinkResponse)
                .toList();
    }

    public TraceLinkResponse createLink(CreateLinkRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TraceLink link = new TraceLink(id, request.requirementId(), request.apiId(), request.caseId(), now);
        repository.saveTraceLink(link);
        contextClient.writeAuditEvent("CREATE", "TRACE_LINK", id.toString(), "SUCCEEDED");
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
                r.id(), r.title(), r.description(), r.status(), r.priority(),
                r.projectId(), r.tags(), r.createdAt(), r.updatedAt()
        );
    }

    private static ApiResponseDTO toApiResponse(AssetApi a) {
        return new ApiResponseDTO(
                a.id(), a.summary(), a.description(), a.httpMethod(), a.path(),
                a.requestSchema(), a.responseSchema(), a.projectId(),
                a.status(), a.createdAt(), a.updatedAt()
        );
    }

    private static PageResponse toPageResponse(AssetPage p) {
        return new PageResponse(
                p.id(), p.name(), p.urlPattern(), p.source(), p.sourceRef(), p.componentTree(), p.screenshotUrl(),
                p.projectId(), p.status(), p.createdAt(), p.updatedAt()
        );
    }

    private static BusinessFlowResponse toBusinessFlowResponse(AssetBusinessFlow f) {
        return new BusinessFlowResponse(
                f.id(), f.name(), f.description(), f.flowJson(), f.priority(),
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
                tc.id(), tc.title(), tc.description(), tc.requirementId(), tc.apiId(),
                tc.status(), tc.priority(), tc.tags(), stepResponses,
                tc.createdAt(), tc.updatedAt()
        );
    }

    private static TraceLinkResponse toTraceLinkResponse(TraceLink l) {
        return new TraceLinkResponse(l.id(), l.requirementId(), l.apiId(), l.caseId(), l.createdAt());
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
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
