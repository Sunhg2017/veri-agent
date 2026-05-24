package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.BusinessFlowResponse;
import com.songhg.veri.agent.asset.application.PageResponse;
import com.songhg.veri.agent.asset.application.RequirementResponse;
import com.songhg.veri.agent.asset.application.TestCaseResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.LifecycleManagedAsset;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetLifecycleService {

    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;

    public AssetLifecycleService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetVersionHistoryService versionHistoryService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.versionHistoryService = versionHistoryService;
    }

    @Transactional
    public RequirementResponse updateRequirementLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetRequirement existing = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "REQUIREMENT",
                existing,
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureRequirementRestoreHasNoConflict(existing);
        }
        AssetRequirement updated = new AssetRequirement(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceUrl(),
                existing.acceptanceCriteria(),
                existing.status(),
                existing.priority(),
                existing.projectId(),
                existing.tags(),
                existing.nextVersion(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit(lifecycleAction(nextLifecycle), "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        versionHistoryService.recordRequirementChange(existing, stored, lifecycleAction(nextLifecycle));
        return AssetResponseMapper.toRequirementResponse(stored);
    }

    @Transactional
    public ApiResponseDTO updateApiLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetApi existing = repository.apiIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "API",
                existing,
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureApiRestoreHasNoConflict(existing);
        }
        AssetApi updated = new AssetApi(
                existing.id(),
                existing.code(),
                existing.summary(),
                existing.description(),
                existing.httpMethod(),
                existing.path(),
                existing.source(),
                existing.sourceRef(),
                existing.version(),
                existing.requestSchema(),
                existing.responseSchema(),
                existing.projectId(),
                existing.status(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit(lifecycleAction(nextLifecycle), "API", id, existing.projectId());
        AssetApi stored = repository.saveApi(updated);
        return AssetResponseMapper.toApiResponse(stored);
    }

    @Transactional
    public PageResponse updatePageLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetPage existing = repository.pageIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "PAGE",
                existing,
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensurePageRestoreHasNoConflict(existing);
        }
        AssetPage updated = new AssetPage(
                existing.id(),
                existing.code(),
                existing.name(),
                existing.urlPattern(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceVersion(),
                existing.componentTree(),
                existing.screenshotUrl(),
                existing.projectId(),
                existing.status(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit(lifecycleAction(nextLifecycle), "PAGE", id, existing.projectId());
        AssetPage stored = repository.savePage(updated);
        return AssetResponseMapper.toPageResponse(stored);
    }

    @Transactional
    public BusinessFlowResponse updateBusinessFlowLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        AssetBusinessFlow existing = repository.businessFlowIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "BUSINESS_FLOW",
                existing,
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureBusinessFlowRestoreHasNoConflict(existing);
        }
        AssetBusinessFlow updated = new AssetBusinessFlow(
                existing.id(),
                existing.code(),
                existing.name(),
                existing.description(),
                existing.flowJson(),
                existing.priority(),
                existing.projectId(),
                existing.status(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit(lifecycleAction(nextLifecycle), "BUSINESS_FLOW", id, existing.projectId());
        AssetBusinessFlow stored = repository.saveBusinessFlow(updated);
        return AssetResponseMapper.toBusinessFlowResponse(stored);
    }

    @Transactional
    public TestCaseResponse updateTestCaseLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        TestCaseRecord existing = repository.testCaseIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        Instant now = Instant.now();
        String nextLifecycle = nextLifecycle(
                "TEST_CASE",
                existing,
                request.lifecycleStatus()
        );
        if ("ACTIVE".equals(nextLifecycle)) {
            ensureTestCaseRestoreHasNoConflict(existing);
        }
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
                existing.steps(),
                existing.nextVersion(),
                nextLifecycle,
                archivedAtFor(nextLifecycle, existing.archivedAt(), now),
                deletedAtFor(nextLifecycle, now),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit(lifecycleAction(nextLifecycle), "TEST_CASE", id, existing.projectId());
        TestCaseRecord stored = repository.saveTestCase(updated);
        versionHistoryService.recordTestCaseChange(existing, stored, lifecycleAction(nextLifecycle));
        return AssetResponseMapper.toTestCaseResponse(stored, stored.steps());
    }

    private String nextLifecycle(
            String resourceType,
            LifecycleManagedAsset asset,
            String rawNextLifecycle
    ) {
        String current = asset.currentLifecycleStatus();
        String next = valueIn(rawNextLifecycle, current, LIFECYCLE_STATUSES, "lifecycleStatus");
        if (!asset.canTransitionLifecycleTo(next)) {
            projectAuditService.writeProjectAudit("LIFECYCLE_CHANGE_DENIED", resourceType, asset.id(), asset.projectId(), "DENIED");
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    resourceType + " 生命周期不允许从 " + current + " 变更为 " + next
            );
        }
        return next;
    }

    private void ensureRequirementRestoreHasNoConflict(AssetRequirement requirement) {
        if (repository.hasActiveRequirementCodeConflict(requirement.projectId(), requirement.code(), requirement.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下需求编码已被其他资产占用，无法恢复: " + requirement.code());
        }
        if ("IMPORT".equals(requirement.source()) && StringUtils.hasText(requirement.sourceRef())
                && repository.hasActiveRequirementSourceRefConflict(
                        requirement.projectId(),
                        requirement.source(),
                        requirement.sourceRef(),
                        requirement.id()
                )) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下导入来源已被其他需求占用，无法恢复: " + requirement.sourceRef());
        }
    }

    private void ensureApiRestoreHasNoConflict(AssetApi api) {
        if (repository.hasActiveApiPathConflict(api.projectId(), api.path(), api.httpMethod(), api.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下 API 路径和方法已被其他资产占用，无法恢复");
        }
    }

    private void ensurePageRestoreHasNoConflict(AssetPage page) {
        if (repository.hasActivePageCodeConflict(page.projectId(), page.code(), page.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下页面编码已被其他资产占用，无法恢复: " + page.code());
        }
    }

    private void ensureBusinessFlowRestoreHasNoConflict(AssetBusinessFlow flow) {
        if (repository.hasActiveBusinessFlowCodeConflict(flow.projectId(), flow.code(), flow.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下业务流编码已被其他资产占用，无法恢复: " + flow.code());
        }
    }

    private void ensureTestCaseRestoreHasNoConflict(TestCaseRecord testCase) {
        if (repository.hasActiveTestCaseCodeConflict(testCase.projectId(), testCase.code(), testCase.id())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同项目下测试用例编码已被其他资产占用，无法恢复: " + testCase.code());
        }
    }

    private static Instant archivedAtFor(String lifecycleStatus, Instant existingArchivedAt, Instant now) {
        return "ARCHIVED".equals(lifecycleStatus) ? (existingArchivedAt == null ? now : existingArchivedAt) : null;
    }

    private static Instant deletedAtFor(String lifecycleStatus, Instant now) {
        return "DELETED".equals(lifecycleStatus) ? now : null;
    }

    private static String lifecycleAction(String lifecycleStatus) {
        return switch (lifecycleStatus) {
            case "ARCHIVED" -> "ARCHIVE";
            case "DELETED" -> "SOFT_DELETE";
            default -> "RESTORE";
        };
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }
}
