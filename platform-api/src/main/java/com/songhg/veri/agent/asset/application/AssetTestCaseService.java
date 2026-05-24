package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.api.request.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.api.request.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.api.request.UpdateTestCaseRequest;
import com.songhg.veri.agent.asset.api.response.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.api.response.TestCaseResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.asset.domain.AssetVersion;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns test case asset CRUD, linked resource scope checks and version/lifecycle entry points.
 */
@Service
public class AssetTestCaseService {

    private static final Logger log = LoggerFactory.getLogger(AssetTestCaseService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetVersionRollbackService versionRollbackService;
    private final AssetLifecycleService lifecycleService;

    public AssetTestCaseService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetVersionHistoryService versionHistoryService,
            AssetVersionRollbackService versionRollbackService,
            AssetLifecycleService lifecycleService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.versionHistoryService = versionHistoryService;
        this.versionRollbackService = versionRollbackService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Resolves project ownership from active or inactive test cases for resource-level authorization.
     */
    public String testCaseProjectScopeId(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return projectAuditService.projectContext(testCase.projectId()).projectId();
    }

    /**
     * Lists active test cases by default and maps embedded steps into the response contract.
     */
    public com.songhg.veri.agent.common.api.PageResponse<TestCaseResponse> listTestCases(
            AssetListRequest request
    ) {
        projectAuditService.validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<TestCaseResponse> items = repository.testCases(query).stream()
                .map(testCase -> AssetResponseMapper.toTestCaseResponse(testCase, testCase.steps()))
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(
                items,
                query.index(),
                query.size(),
                repository.countTestCases(query)
        );
    }

    /**
     * Reads non-deleted test cases for normal detail and editing flows.
     */
    public TestCaseResponse getTestCase(UUID id) {
        TestCaseRecord testCase = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return AssetResponseMapper.toTestCaseResponse(testCase, testCase.steps());
    }

    /**
     * Reads lifecycle-visible test case details and validates the test case's project scope.
     */
    public TestCaseResponse getTestCaseIncludingInactive(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        projectAuditService.validateProjectWhenProvided(testCase.projectId());
        return AssetResponseMapper.toTestCaseResponse(testCase, testCase.steps());
    }

    /**
     * Creates a manual test case after confirming linked requirement/API assets belong to the same project.
     */
    @Transactional
    public TestCaseResponse createTestCase(CreateTestCaseRequest request) {
        String scopeId = projectAuditService.projectContext(request.projectId()).projectId();
        validateRequirementBelongsToProject(request.requirementId(), request.projectId());
        validateApiBelongsToProject(request.apiId(), request.projectId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        List<TestCaseStep> steps = buildSteps(id, request.steps());
        TestCaseRecord testCase = new TestCaseRecord(
                id,
                assetCode("TC", id),
                request.title(),
                request.description(),
                request.projectId(),
                request.requirementId(),
                request.apiId(),
                "MANUAL",
                null,
                initialStatus(request.status()),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.tags(),
                steps,
                AssetVersion.initial(),
                STATUS_ACTIVE,
                null,
                null,
                now,
                now
        );
        projectAuditService.writeProjectAudit("CREATE", "TEST_CASE", id, scopeId);
        TestCaseRecord stored = repository.saveTestCase(testCase);
        versionHistoryService.recordTestCaseCreated(stored);
        log.info("Created test case id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        return AssetResponseMapper.toTestCaseResponse(stored, steps);
    }

    /**
     * Updates test case metadata, preserves existing steps and records version history for review-state changes.
     */
    @Transactional
    public TestCaseResponse updateTestCase(UUID id, UpdateTestCaseRequest request) {
        TestCaseRecord existing = repository.testCase(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        validateRequirementBelongsToProject(request.requirementId(), existing.projectId());
        validateApiBelongsToProject(request.apiId(), existing.projectId());
        List<TestCaseStep> existingSteps = existing.steps();
        Instant now = Instant.now();
        String nextStatus = nextTestCaseStatus(existing, request.status());
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
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit("UPDATE", "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        versionHistoryService.recordTestCaseChange(existing, stored, "UPDATE");
        return AssetResponseMapper.toTestCaseResponse(stored, existingSteps);
    }

    /**
     * Returns immutable version history for active or inactive test cases.
     */
    public List<AssetVersionHistoryResponse> testCaseVersions(UUID id) {
        TestCaseRecord testCase = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        return versionHistoryService.responses("TEST_CASE", testCase.id());
    }

    /**
     * Delegates rollback to the shared rollback service so snapshot restore rules stay consistent.
     */
    @Transactional
    public TestCaseResponse rollbackTestCaseVersion(UUID id, int version, RollbackAssetVersionRequest request) {
        return versionRollbackService.rollbackTestCaseVersion(id, version, request);
    }

    /**
     * Delegates lifecycle transitions to the shared lifecycle service so archive/delete/restore rules stay aligned.
     */
    @Transactional
    public TestCaseResponse updateTestCaseLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateTestCaseLifecycle(id, request);
    }

    /**
     * Converts request steps into stable, ordered child records under the new test case id.
     */
    private static List<TestCaseStep> buildSteps(UUID caseId, List<CreateTestCaseRequest.StepDto> requestedSteps) {
        List<CreateTestCaseRequest.StepDto> steps = Optional.ofNullable(requestedSteps)
                .orElse(Collections.emptyList());
        List<TestCaseStep> records = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            CreateTestCaseRequest.StepDto step = steps.get(index);
            records.add(new TestCaseStep(UUID.randomUUID(), caseId, index, step.action(), step.expectedResult()));
        }
        return records;
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

    private static void ensureSameProject(
            String resourceName,
            UUID resourceId,
            String actualProjectId,
            String expectedProjectId
    ) {
        if (!StringUtils.hasText(actualProjectId) || !actualProjectId.equals(expectedProjectId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    resourceName + "不属于当前项目: " + resourceId
            );
        }
    }

    private String nextTestCaseStatus(TestCaseRecord testCase, String rawNextStatus) {
        String nextStatus = valueIn(rawNextStatus, testCase.status(), REVIEW_STATUSES, "status");
        if (!testCase.canTransitionReviewStatusTo(nextStatus)) {
            projectAuditService.writeProjectAudit(
                    "STATUS_CHANGE_DENIED",
                    "TEST_CASE",
                    testCase.id(),
                    testCase.projectId(),
                    "DENIED"
            );
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    "TEST_CASE 状态不允许从 " + testCase.status() + " 变更为 " + nextStatus
            );
        }
        return nextStatus;
    }

    private static AssetListQuery assetListQuery(AssetListRequest request) {
        return new AssetListQuery(
                trimToNull(request.getProjectId()),
                lifecycleFilter(request.getLifecycleStatus()),
                request.getStatus(),
                request.getSource(),
                request.getKeyword(),
                request.toPageQuery()
        );
    }

    private static String lifecycleFilter(String rawValue) {
        return valueIn(rawValue, STATUS_ACTIVE, LIFECYCLE_STATUSES, "lifecycleStatus");
    }

    private static String initialStatus(String rawValue) {
        return valueIn(rawValue, STATUS_DRAFT, REVIEW_STATUSES, "status");
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
