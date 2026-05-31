package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.testdesign.application.command.TestDesignPublishCommand;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdesign.infrastructure.InMemoryTestDesignRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;

class TestDesignPublishServiceTest {

    private final InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
    private final AssetService assetService = new AssetService(new InMemoryAssetRepository(), platformContextClient());
    private final TestDesignProperties properties = properties(true);
    private final TestDesignResponseMapper responseMapper = new TestDesignResponseMapper(
            new ObjectMapper().findAndRegisterModules(),
            new InMemoryModelAccessRepository(),
            new InMemoryModelInvocationJobRepository(),
            properties
    );
    private final TestDesignQualityService qualityService = new TestDesignQualityService(
            repository,
            responseMapper,
            properties
    );
    private final TestDesignPublishService publishService = new TestDesignPublishService(
            repository,
            assetService,
            new TestDesignActorResolver(mock(AuthorizationService.class)),
            qualityService,
            responseMapper,
            properties,
            mock(TestDesignEventPublisher.class)
    );

    @Test
    void releaseReadinessBlockingRejectsOfficialPublishButAllowsDryRun() {
        RequirementResponse requirement = createRequirement();
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        repository.saveTask(task(taskId, requirement.id(), requirement.projectId()));
        repository.saveCandidate(blockedCandidate(candidateId, taskId, requirement.id(), requirement.projectId()));

        TestDesignPublishResponse dryRun = publishService.publishDryRun(taskId,
                new TestDesignPublishCommand(List.of(candidateId), true));

        assertThat(dryRun.dryRun()).isTrue();
        assertThat(dryRun.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.action()).isEqualTo("CREATE");
                    assertThat(record.result()).isEqualTo("PLANNED");
                });
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> publishService.publish(taskId, new TestDesignPublishCommand(List.of(candidateId), false)));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(exception.getMessage())
                .contains("WP5 发布准出质量门禁不通过")
                .contains("readiness=BLOCKED")
                .contains("blockingCount=");
        assertThat(repository.candidate(candidateId)).get()
                .extracting(TestDesignCandidate::status, TestDesignCandidate::assetCaseId)
                .containsExactly(TestDesignCandidateStatus.CONFIRMED.name(), null);
        assertThat(repository.publishRecords(taskId)).isEmpty();
        AssetListRequest request = new AssetListRequest();
        request.setProjectId(requirement.projectId());
        assertThat(assetService.listTestCases(request).items()).isEmpty();
    }

    private RequirementResponse createRequirement() {
        return assetService.createRequirement(new CreateRequirementRequest(
                "WP5 发布准出阻断需求",
                "质量门禁阻断正式发布",
                "APPROVED",
                "HIGH",
                "project-wp5-release-gate",
                "wp5",
                "IMPORT",
                "REQ-" + UUID.randomUUID(),
                null,
                "正式发布前必须通过准出质量门禁"
        ));
    }

    private static TestDesignTask task(UUID id, UUID requirementId, String projectId) {
        Instant now = Instant.now();
        return new TestDesignTask(
                id,
                projectId,
                "WP5 release readiness task",
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
                1,
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

    private static TestDesignCandidate blockedCandidate(
            UUID id,
            UUID taskId,
            UUID requirementId,
            String projectId
    ) {
        Instant now = Instant.now();
        return new TestDesignCandidate(
                id,
                taskId,
                projectId,
                requirementId,
                null,
                "准出阻断候选",
                "正式发布前会因为聚合质量门禁失败而阻断",
                "SMOKE",
                "HIGH",
                TestDesignCandidateStatus.CONFIRMED.name(),
                "前置条件",
                "[{\"action\":\"执行准出检查\",\"expectedResult\":\"\"}]",
                "",
                "wp5",
                requirementId + ":SMOKE:准出阻断候选",
                0.95D,
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                "RULE_TEMPLATE",
                null,
                null,
                null,
                null,
                null,
                "wp5-user",
                now.minusSeconds(8),
                1L,
                now.minusSeconds(9),
                now.minusSeconds(4)
        );
    }

    private static TestDesignProperties properties(boolean releaseReadinessPublishBlockingEnabled) {
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
                true,
                100,
                600,
                120,
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
                releaseReadinessPublishBlockingEnabled,
                0.86D,
                0.90D,
                true,
                50,
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
