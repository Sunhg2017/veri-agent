package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.api.response.TestCaseStepResponse;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetTestCaseStepService {

    private final AssetRepository repository;
    private final PlatformContextClient contextClient;
    private final AssetVersionHistoryService versionHistoryService;

    public AssetTestCaseStepService(
            AssetRepository repository,
            PlatformContextClient contextClient,
            AssetVersionHistoryService versionHistoryService
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.versionHistoryService = versionHistoryService;
    }

    public List<TestCaseStepResponse> listTestCaseSteps(UUID caseId) {
        requireTestCase(caseId);
        return repository.testCaseSteps(caseId).stream()
                .sorted(Comparator.comparingInt(TestCaseStep::stepOrder))
                .map(AssetTestCaseStepService::toTestCaseStepResponse)
                .toList();
    }

    @Transactional
    public List<TestCaseStepResponse> updateTestCaseSteps(UUID caseId, UpdateTestCaseStepsRequest request) {
        TestCaseRecord existing = requireTestCase(caseId);
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
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        TestCaseRecord stored = repository.saveTestCase(updated);
        versionHistoryService.recordTestCaseChange(existing, stored, "STEPS_UPDATE");
        return steps.stream()
                .map(AssetTestCaseStepService::toTestCaseStepResponse)
                .toList();
    }

    private TestCaseRecord requireTestCase(UUID caseId) {
        return repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
    }

    private void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId) {
        String scopeId = StringUtils.hasText(projectId) ? projectContext(projectId).projectId() : null;
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), scopeId, "SUCCEEDED");
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

    private static TestCaseStepResponse toTestCaseStepResponse(TestCaseStep step) {
        return new TestCaseStepResponse(step.stepOrder(), step.action(), step.expectedResult());
    }
}
