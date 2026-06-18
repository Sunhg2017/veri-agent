package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.query.TraceLinkListRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TestDesignAsyncPublishServiceTest {

    private final InMemoryTestDesignRepository repository = new InMemoryTestDesignRepository();
    private final AssetService assetService = new AssetService(new com.songhg.veri.agent.asset.infrastructure.InMemoryAssetRepository(),
            platformContextClient());
    private final TestDesignEventPublisher eventPublisher = mock(TestDesignEventPublisher.class);
    private final AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
    private final TestDesignProperties properties = properties();
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
    private final TestDesignActorResolver actorResolver = new TestDesignActorResolver(mock(AuthorizationService.class));
    private final TestDesignReleaseReadinessApprovalService releaseReadinessApprovalService =
            new TestDesignReleaseReadinessApprovalService(
                    repository,
                    qualityService,
                    actorResolver,
                    mock(TestDesignPlatformContextClient.class)
            );
    private final TestDesignPublishService publishService = new TestDesignPublishService(
            repository,
            assetService,
            actorResolver,
            qualityService,
            releaseReadinessApprovalService,
            responseMapper,
            properties,
            eventPublisher,
            notificationService
    );

    @Test
    void queuesFormalPublishThenConsumerWritesWp3Idempotently() {
        RequirementResponse requirement = createRequirement();
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        repository.saveTask(task(taskId, requirement));
        repository.saveCandidate(candidate(candidateId, taskId, requirement, TestDesignCandidateStatus.CONFIRMED.name()));

        TestDesignPublishResponse queued = publishService.publish(taskId,
                new TestDesignPublishCommand(List.of(candidateId), false));

        assertThat(queued.dryRun()).isFalse();
        assertThat(queued.created()).isZero();
        assertThat(queued.createdCaseIds()).isEmpty();
        assertThat(queued.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.action()).isEqualTo("CREATE");
                    assertThat(record.result()).isEqualTo("QUEUED");
                    assertThat(record.candidateStatus()).isEqualTo(TestDesignCandidateStatus.PUBLISH_QUEUED.name());
                });
        assertThat(repository.task(taskId)).get().extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.PUBLISH_QUEUED.name());
        assertThat(repository.candidate(candidateId)).get().extracting(TestDesignCandidate::status)
                .isEqualTo(TestDesignCandidateStatus.PUBLISH_QUEUED.name());
        verify(eventPublisher).publishPublishRequested(taskId, List.of(candidateId));
        assertThat(testCases(requirement.projectId())).isEmpty();

        TestDesignPublishResponse processed = publishService.processQueuedPublish(taskId, List.of(candidateId));

        assertThat(processed.created()).isEqualTo(1);
        assertThat(processed.createdCaseIds()).hasSize(1);
        assertThat(processed.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.action()).isEqualTo("CREATE");
                    assertThat(record.result()).isEqualTo("SUCCEEDED");
                    assertThat(record.candidateStatus()).isEqualTo(TestDesignCandidateStatus.PUBLISHED.name());
                });
        UUID caseId = processed.createdCaseIds().getFirst();
        assertThat(repository.task(taskId)).get()
                .satisfies(task -> {
                    assertThat(task.status()).isEqualTo(TestDesignTaskStatus.PUBLISHED.name());
                    assertThat(task.publishedCount()).isEqualTo(1);
                });
        assertThat(repository.candidate(candidateId)).get()
                .extracting(TestDesignCandidate::status, TestDesignCandidate::assetCaseId)
                .containsExactly(TestDesignCandidateStatus.PUBLISHED.name(), caseId);
        assertThat(repository.publishRecords(taskId)).hasSize(1);
        assertThat(testCases(requirement.projectId())).hasSize(1);
        TraceLinkListRequest linkRequest = new TraceLinkListRequest();
        linkRequest.setRequirementId(requirement.id());
        linkRequest.setCaseId(caseId);
        assertThat(assetService.listLinks(linkRequest).total()).isEqualTo(1);
        verify(notificationService).notifyTestDesignPublishFinished(org.mockito.ArgumentMatchers.argThat(
                task -> taskId.equals(task.id())
        ), org.mockito.ArgumentMatchers.argThat(response -> response.created() == 1 && response.failed() == 0));

        TestDesignPublishResponse replay = publishService.processQueuedPublish(taskId, List.of(candidateId));

        assertThat(replay.records()).isEmpty();
        assertThat(repository.publishRecords(taskId)).hasSize(1);
        assertThat(testCases(requirement.projectId())).hasSize(1);
    }

    @Test
    void recoveryReemitsOnlyQueuedCandidatesAndDoesNotExpandScope() {
        RequirementResponse requirement = createRequirement();
        UUID taskId = UUID.randomUUID();
        UUID queuedCandidateId = UUID.randomUUID();
        UUID confirmedCandidateId = UUID.randomUUID();
        repository.saveTask(withTaskStatus(task(taskId, requirement), TestDesignTaskStatus.PUBLISH_QUEUED));
        repository.saveCandidate(candidate(queuedCandidateId, taskId, requirement,
                TestDesignCandidateStatus.PUBLISH_QUEUED.name()));
        repository.saveCandidate(candidate(confirmedCandidateId, taskId, requirement,
                TestDesignCandidateStatus.CONFIRMED.name()));
        TestDesignPublishEventRecoveryService recoveryService = new TestDesignPublishEventRecoveryService(
                repository,
                eventPublisher,
                properties,
                "30 */2 * * * *"
        );

        TestDesignPublishEventRecoveryService.PublishRecoveryResult result =
                recoveryService.recoverQueuedPublishes("test");

        assertThat(result.queuedTaskEvents()).isEqualTo(1);
        assertThat(result.queuedCandidateCount()).isEqualTo(1);
        verify(eventPublisher).publishPublishRequested(taskId, List.of(queuedCandidateId));
    }

    @Test
    void publishConsumerKeepsTaskInFlightUntilAllQueuedCandidatesFinish() {
        RequirementResponse requirement = createRequirement();
        UUID taskId = UUID.randomUUID();
        UUID firstCandidateId = UUID.randomUUID();
        UUID secondCandidateId = UUID.randomUUID();
        repository.saveTask(withTaskStatus(task(taskId, requirement), TestDesignTaskStatus.PUBLISH_QUEUED));
        repository.saveCandidate(candidate(firstCandidateId, taskId, requirement,
                TestDesignCandidateStatus.PUBLISH_QUEUED.name()));
        repository.saveCandidate(candidate(secondCandidateId, taskId, requirement,
                TestDesignCandidateStatus.PUBLISH_QUEUED.name()));

        TestDesignPublishResponse processed = publishService.processQueuedPublish(taskId, List.of(firstCandidateId));

        assertThat(processed.created()).isEqualTo(1);
        assertThat(repository.candidate(firstCandidateId)).get().extracting(TestDesignCandidate::status)
                .isEqualTo(TestDesignCandidateStatus.PUBLISHED.name());
        assertThat(repository.candidate(secondCandidateId)).get().extracting(TestDesignCandidate::status)
                .isEqualTo(TestDesignCandidateStatus.PUBLISH_QUEUED.name());
        assertThat(repository.task(taskId)).get()
                .satisfies(task -> {
                    assertThat(task.status()).isEqualTo(TestDesignTaskStatus.PUBLISHING.name());
                    assertThat(task.publishedCount()).isEqualTo(1);
                });
    }

    @Test
    void stalePublishingRecoveryFailsCandidateAndClosesTransientTask() {
        RequirementResponse requirement = createRequirement();
        UUID taskId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        repository.saveTask(withTaskStatus(task(taskId, requirement), TestDesignTaskStatus.PUBLISHING));
        repository.saveCandidate(withCandidateUpdatedAt(candidate(candidateId, taskId, requirement,
                TestDesignCandidateStatus.PUBLISHING.name()), Instant.now().minusSeconds(120)));
        TestDesignPublishEventRecoveryService recoveryService = new TestDesignPublishEventRecoveryService(
                repository,
                eventPublisher,
                propertiesWithPublishTimeout(60),
                "30 */2 * * * *"
        );

        TestDesignPublishEventRecoveryService.PublishRecoveryResult result =
                recoveryService.recoverQueuedPublishes("stale");

        assertThat(result.timedOutPublishingCandidates()).isEqualTo(1);
        assertThat(result.closedTransientTasks()).isEqualTo(1);
        assertThat(repository.candidate(candidateId)).get()
                .extracting(TestDesignCandidate::status, TestDesignCandidate::errorMessage)
                .containsExactly(TestDesignCandidateStatus.FAILED.name(), "发布任务运行超时，已由恢复扫描标记失败，可重试");
        assertThat(repository.task(taskId)).get().extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.SUCCEEDED.name());
        verify(eventPublisher, never()).publishPublishRequested(taskId, List.of(candidateId));
    }

    private RequirementResponse createRequirement() {
        return assetService.createRequirement(new CreateRequirementRequest(
                "WP5 异步发布需求",
                "验证发布跨 WP 异步编排",
                "APPROVED",
                "HIGH",
                "project-wp5-async-publish",
                "wp5",
                "IMPORT",
                "REQ-" + UUID.randomUUID(),
                null,
                "候选正式发布后应异步写入 WP3"
        ));
    }

    private List<TestCaseResponse> testCases(String projectId) {
        AssetListRequest request = new AssetListRequest();
        request.setProjectId(projectId);
        PageResponse<TestCaseResponse> response = assetService.listTestCases(request);
        return response.items();
    }

    private static TestDesignTask task(UUID id, RequirementResponse requirement) {
        Instant now = Instant.now();
        return new TestDesignTask(
                id,
                requirement.projectId(),
                "WP5 async publish task",
                TestDesignTaskStatus.SUCCEEDED.name(),
                requirement.id().toString(),
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

    private static TestDesignTask withTaskStatus(TestDesignTask task, TestDesignTaskStatus status) {
        return new TestDesignTask(
                task.id(),
                task.projectId(),
                task.title(),
                status.name(),
                task.requirementIds(),
                task.coverageTypes(),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                task.totalRequirements(),
                task.generatedCount(),
                task.confirmedCount(),
                task.publishedCount(),
                task.errorMessage(),
                task.requestedBy(),
                task.idempotencyKey(),
                task.requestDigest(),
                task.inputDigest(),
                task.contextSummaryJson(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private static TestDesignCandidate candidate(
            UUID id,
            UUID taskId,
            RequirementResponse requirement,
            String status
    ) {
        Instant now = Instant.now();
        return new TestDesignCandidate(
                id,
                taskId,
                requirement.projectId(),
                requirement.id(),
                null,
                "异步发布候选 " + id,
                "发布消费者应写入 WP3",
                "SMOKE",
                "HIGH",
                status,
                "前置条件",
                "[{\"action\":\"执行发布\",\"expectedResult\":\"写入 WP3\"}]",
                "写入 WP3",
                "wp5",
                requirement.id() + ":SMOKE:" + id,
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

    private static TestDesignCandidate withCandidateUpdatedAt(TestDesignCandidate candidate, Instant updatedAt) {
        return new TestDesignCandidate(
                candidate.id(),
                candidate.taskId(),
                candidate.projectId(),
                candidate.requirementId(),
                candidate.apiId(),
                candidate.title(),
                candidate.description(),
                candidate.coverageType(),
                candidate.priority(),
                candidate.status(),
                candidate.preconditions(),
                candidate.stepsJson(),
                candidate.expectedResult(),
                candidate.tags(),
                candidate.duplicateKey(),
                candidate.confidence(),
                candidate.promptKey(),
                candidate.promptVersion(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                candidate.assetCaseId(),
                candidate.reviewComment(),
                candidate.rejectedReason(),
                candidate.ignoredReason(),
                candidate.errorMessage(),
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version(),
                candidate.createdAt(),
                updatedAt
        );
    }

    private static TestDesignProperties properties() {
        return propertiesWithPublishTimeout(600);
    }

    private static TestDesignProperties propertiesWithPublishTimeout(long publishRunningTimeoutSeconds) {
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
                publishRunningTimeoutSeconds,
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
