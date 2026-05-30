package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.infrastructure.InMemoryTestDesignRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TestDesignPublishCompensationServiceTest {

    private final InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
    private final InMemoryAssetRepository assetRepository = new InMemoryAssetRepository();
    private final AssetService assetService = new AssetService(assetRepository, platformContextClient());
    private final TestDesignResponseMapper responseMapper = new TestDesignResponseMapper(
            new ObjectMapper().findAndRegisterModules(),
            new InMemoryModelAccessRepository(),
            new InMemoryModelInvocationJobRepository(),
            properties(true, 50)
    );
    private final TestDesignQualityService qualityService = new TestDesignQualityService(
            repository,
            responseMapper,
            properties(true, 50)
    );
    private final TestDesignPublishService publishService = new TestDesignPublishService(
            repository,
            assetService,
            new TestDesignActorResolver(mock(AuthorizationService.class)),
            qualityService,
            responseMapper,
            properties(true, 50)
    );

    @Test
    void compensatesFailedCandidateWithExistingWp3CaseReference() {
        RequirementResponse requirement = createRequirement("project-wp5-compensate");
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        repository.saveTask(task(taskId, requirement.id(), "project-wp5-compensate"));
        TestCaseResponse testCase = createAiTestCase("project-wp5-compensate", requirement.id(), candidateId);
        repository.saveCandidate(candidate(
                candidateId,
                taskId,
                requirement.id(),
                "project-wp5-compensate",
                TestDesignCandidateStatus.FAILED.name(),
                testCase.id()
        ));
        TestDesignPublishCompensationService service = new TestDesignPublishCompensationService(
                repository,
                publishService,
                properties(true, 50),
                "0 */5 * * * *"
        );

        TestDesignPublishCompensationService.CompensationResult result =
                service.compensateFailedLinkedCandidates("test");

        assertThat(result.scannedCandidates()).isEqualTo(1);
        assertThat(result.succeededCandidates()).isEqualTo(1);
        assertThat(result.failedCandidates()).isZero();
        assertThat(repository.candidate(candidateId)).get()
                .extracting(TestDesignCandidate::status, TestDesignCandidate::assetCaseId, TestDesignCandidate::errorMessage)
                .containsExactly(TestDesignCandidateStatus.PUBLISHED.name(), testCase.id(), null);
        assertThat(repository.publishRecords(taskId))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.action()).isEqualTo(TestDesignPublishService.ACTION_AUTO_COMPENSATE_LINK_EXISTING);
                    assertThat(record.result()).isEqualTo("SUCCEEDED");
                    assertThat(record.assetCaseId()).isEqualTo(testCase.id());
                    assertThat(record.publishedBy()).isEqualTo("system");
                });
        TraceLinkListRequest links = new TraceLinkListRequest();
        links.setRequirementId(requirement.id());
        links.setCaseId(testCase.id());
        assertThat(assetService.listLinks(links).total()).isEqualTo(1);
        assertThat(repository.task(taskId)).get()
                .extracting(TestDesignTask::status, TestDesignTask::publishedCount)
                .containsExactly(TestDesignTaskStatus.PUBLISHED.name(), 1);
    }

    @Test
    void skipsDisabledCompensation() {
        RequirementResponse requirement = createRequirement("project-wp5-disabled");
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        repository.saveTask(task(taskId, requirement.id(), "project-wp5-disabled"));
        TestCaseResponse testCase = createAiTestCase("project-wp5-disabled", requirement.id(), candidateId);
        repository.saveCandidate(candidate(
                candidateId,
                taskId,
                requirement.id(),
                "project-wp5-disabled",
                TestDesignCandidateStatus.FAILED.name(),
                testCase.id()
        ));
        TestDesignPublishCompensationService service = new TestDesignPublishCompensationService(
                repository,
                publishService,
                properties(false, 50),
                "0 */5 * * * *"
        );

        TestDesignPublishCompensationService.CompensationResult result =
                service.compensateFailedLinkedCandidates("disabled");

        assertThat(result.scannedCandidates()).isZero();
        assertThat(repository.candidate(candidateId)).get()
                .extracting(TestDesignCandidate::status)
                .isEqualTo(TestDesignCandidateStatus.FAILED.name());
        assertThat(repository.publishRecords(taskId)).isEmpty();
    }

    @Test
    void doesNotRecordDuplicateAutomaticCompensationAfterLockReread() {
        RequirementResponse requirement = createRequirement("project-wp5-duplicate-compensate");
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        repository.saveTask(task(taskId, requirement.id(), "project-wp5-duplicate-compensate"));
        TestCaseResponse testCase = createAiTestCase("project-wp5-duplicate-compensate", requirement.id(), candidateId);
        TestDesignCandidate candidate = candidate(
                candidateId,
                taskId,
                requirement.id(),
                "project-wp5-duplicate-compensate",
                TestDesignCandidateStatus.FAILED.name(),
                testCase.id()
        );
        repository.saveCandidate(candidate);
        repository.savePublishRecord(publishRecord(
                taskId,
                candidateId,
                candidate.projectId(),
                requirement.id(),
                testCase.id(),
                TestDesignPublishService.ACTION_AUTO_COMPENSATE_LINK_EXISTING,
                "FAILED"
        ));

        var replay = publishService.compensateFailedLinkedCandidate(candidate, "system");

        assertThat(replay.result()).isEqualTo("SKIPPED");
        assertThat(replay.errorMessage()).isEqualTo("候选已执行自动补偿尝试");
        assertThat(repository.publishRecords(taskId)).hasSize(1);
        assertThatThrownBy(() -> repository.savePublishRecord(publishRecord(
                taskId,
                candidateId,
                candidate.projectId(),
                requirement.id(),
                testCase.id(),
                TestDesignPublishService.ACTION_AUTO_COMPENSATE_LINK_EXISTING,
                "FAILED"
        ))).isInstanceOf(IllegalStateException.class);
    }

    private RequirementResponse createRequirement(String projectId) {
        return assetService.createRequirement(new CreateRequirementRequest(
                "WP5 补偿需求",
                "补偿发布用例",
                "APPROVED",
                "HIGH",
                projectId,
                "wp5",
                "IMPORT",
                "REQ-" + UUID.randomUUID(),
                null,
                "补偿验收"
        ));
    }

    private TestCaseResponse createAiTestCase(String projectId, UUID requirementId, UUID candidateId) {
        return assetService.createTestCase(new CreateTestCaseRequest(
                "部分成功遗留用例",
                "补偿后台仅复用已存在用例",
                requirementId,
                null,
                projectId,
                "DRAFT",
                "HIGH",
                "wp5",
                List.of(new CreateTestCaseRequest.StepDto("执行补偿流程", "补偿流程成功")),
                "AI_GENERATED",
                "wp5:" + candidateId
        ));
    }

    private static TestDesignTask task(UUID id, UUID requirementId, String projectId) {
        Instant now = Instant.now();
        return new TestDesignTask(
                id,
                projectId,
                "WP5 publish compensation task",
                TestDesignTaskStatus.SUCCEEDED.name(),
                requirementId.toString(),
                "SMOKE",
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                "RULE_TEMPLATE",
                1,
                1,
                0,
                0,
                null,
                "wp5-user",
                null,
                null,
                "digest-" + id,
                "{}",
                now.minusSeconds(10),
                now.minusSeconds(5)
        );
    }

    private static TestDesignCandidate candidate(
            UUID id,
            UUID taskId,
            UUID requirementId,
            String projectId,
            String status,
            UUID assetCaseId
    ) {
        Instant now = Instant.now();
        return new TestDesignCandidate(
                id,
                taskId,
                projectId,
                requirementId,
                null,
                "补偿候选",
                "补偿候选说明",
                "SMOKE",
                "HIGH",
                status,
                "前置条件",
                "[{\"action\":\"执行补偿流程\",\"expectedResult\":\"补偿流程成功\"}]",
                "补偿流程成功",
                "wp5",
                requirementId + ":SMOKE:补偿候选",
                0.95D,
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                "RULE_TEMPLATE",
                assetCaseId,
                null,
                null,
                null,
                "追踪链接创建失败",
                "wp5-user",
                now.minusSeconds(8),
                2L,
                now.minusSeconds(9),
                now.minusSeconds(4)
        );
    }

    private static com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord publishRecord(
            UUID taskId,
            UUID candidateId,
            String projectId,
            UUID requirementId,
            UUID assetCaseId,
            String action,
            String result
    ) {
        return new com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord(
                UUID.randomUUID(),
                taskId,
                candidateId,
                projectId,
                requirementId,
                assetCaseId,
                false,
                action,
                result,
                null,
                "test",
                Instant.now()
        );
    }

    private static TestDesignProperties properties(boolean compensationEnabled, int compensationBatchSize) {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "RULE_TEMPLATE",
                "wp5-test-design-v1",
                "1.0.0",
                false,
                20,
                3,
                5,
                5,
                5,
                240,
                240,
                240,
                100,
                true,
                true,
                100,
                600,
                120,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                false,
                0.86D,
                0.90D,
                compensationEnabled,
                compensationBatchSize,
                180,
                false,
                true
        );
    }

    private static PlatformContextClient platformContextClient() {
        return new PlatformContextClient() {
            @Override
            public ProjectContext getProjectContext(String projectId) {
                return new ProjectContext(projectId, "ACTIVE", "INTERNAL", false);
            }

            @Override
            public void writeAuditEvent(
                    String action,
                    String resourceType,
                    String resourceId,
                    String scopeId,
                    String result
            ) {
            }
        };
    }
}
