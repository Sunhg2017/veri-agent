package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.command.UpdateTestCaseStepsRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
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


@Service
public class AssetTestCaseStepService {

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;

    public AssetTestCaseStepService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetVersionHistoryService versionHistoryService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
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
        projectAuditService.writeProjectAudit("UPDATE", "TEST_CASE_STEPS", caseId, existing.projectId());
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

    private static TestCaseStepResponse toTestCaseStepResponse(TestCaseStep step) {
        return new TestCaseStepResponse(step.stepOrder(), step.action(), step.expectedResult());
    }
}
